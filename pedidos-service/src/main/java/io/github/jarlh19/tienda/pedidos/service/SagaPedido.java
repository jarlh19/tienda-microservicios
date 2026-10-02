package io.github.jarlh19.tienda.pedidos.service;

import io.github.jarlh19.tienda.pedidos.client.InventarioClient;
import io.github.jarlh19.tienda.pedidos.client.PagosClient;
import io.github.jarlh19.tienda.pedidos.client.ResultadoPago;
import io.github.jarlh19.tienda.pedidos.client.ResultadoReserva;
import io.github.jarlh19.tienda.pedidos.config.SagaProperties;
import io.github.jarlh19.tienda.pedidos.model.EstadoPedido;
import io.github.jarlh19.tienda.pedidos.model.Pedido;
import io.github.jarlh19.tienda.pedidos.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orquestador de la saga de un pedido: 1) reservar stock en inventario, 2) cobrar en pagos. Si un
 * paso falla, se deshacen los anteriores con su compensación (liberar stock, reembolsar).
 *
 * <p>No es @Transactional a propósito: una transacción de base de datos no puede abarcar llamadas
 * a otros servicios. En su lugar, cada cambio de estado se guarda apenas ocurre, así el pedido
 * siempre dice en qué paso quedó y la saga se puede retomar si algo se corta.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SagaPedido {

    private final PedidoRepository pedidos;
    private final InventarioClient inventario;
    private final PagosClient pagos;
    private final SagaProperties propiedades;

    public Pedido ejecutar(Pedido pedido) {
        log.info("Pedido {}: inicia la saga", pedido.getId());

        // Paso 1: reservar stock
        switch (inventario.reservar(pedido.getId(), pedido.getLineas())) {
            case ResultadoReserva.Reservada reservada -> pedido.stockReservado(reservada.total());
            case ResultadoReserva.Rechazada rechazada -> {
                pedido.cancelar(rechazada.motivo());
                return terminar(pedido);
            }
            case ResultadoReserva.NoDisponible caida when !caida.llamadaEnviada() -> {
                // Circuito abierto: la petición no salió, no hay nada que deshacer.
                pedido.cancelar("INVENTARIO_NO_DISPONIBLE: " + caida.detalle());
                return terminar(pedido);
            }
            case ResultadoReserva.NoDisponible caida -> {
                // La petición salió y no hubo respuesta: la reserva pudo hacerse. Se libera por si acaso.
                pedido.compensar("INVENTARIO_NO_DISPONIBLE: " + caida.detalle(), true, false);
                return compensar(pedidos.save(pedido));
            }
        }
        pedido = pedidos.save(pedido);

        // Paso 2: cobrar
        switch (pagos.cobrar(pedido.getId(), pedido.getTotal())) {
            case ResultadoPago.Aprobado aprobado -> pedido.confirmar();
            case ResultadoPago.Rechazado rechazado -> pedido.compensar(rechazado.motivo(), true, false);
            case ResultadoPago.NoDisponible caida ->
                    // Si la petición salió, el cobro pudo hacerse: también hay que reembolsar.
                    pedido.compensar("PAGOS_NO_DISPONIBLE: " + caida.detalle(), true, caida.llamadaEnviada());
        }
        pedido = pedidos.save(pedido);

        return pedido.getEstado() == EstadoPedido.COMPENSANDO ? compensar(pedido) : terminar(pedido);
    }

    /**
     * Ejecuta las compensaciones pendientes en orden inverso a los pasos: primero el pago, después
     * el stock. Las dos son idempotentes, así que repetirlas no causa daño. Lo que no se logre queda
     * pendiente y lo reintenta {@link ReintentosSaga}.
     */
    public Pedido compensar(Pedido pedido) {
        if (pedido.isReembolsoPendiente() && pagos.reembolsar(pedido.getId())) {
            pedido.pagoReembolsado();
        }
        if (pedido.isLiberarStockPendiente() && inventario.liberar(pedido.getId())) {
            pedido.stockLiberado();
        }
        if (pedido.getEstado() != EstadoPedido.COMPENSANDO) {
            return terminar(pedido);
        }

        int intentos = pedido.registrarIntentoFallido();
        Pedido guardado = pedidos.save(pedido);
        if (intentos >= propiedades.alertarTrasIntentos()) {
            // En producción, este log dispararía una alerta: el pedido tiene stock o dinero retenido.
            log.error("Pedido {}: {} intentos sin poder compensar {}; requiere revisión manual",
                    guardado.getId(), intentos, guardado.compensacionesPendientes());
        } else {
            log.warn("Pedido {}: quedan compensaciones pendientes {} (intento {})",
                    guardado.getId(), guardado.compensacionesPendientes(), intentos);
        }
        return guardado;
    }

    private Pedido terminar(Pedido pedido) {
        Pedido guardado = pedidos.save(pedido);
        log.info("Pedido {}: saga terminada en {}{}", guardado.getId(), guardado.getEstado(),
                guardado.getMotivo() == null ? "" : " (" + guardado.getMotivo() + ")");
        return guardado;
    }
}
