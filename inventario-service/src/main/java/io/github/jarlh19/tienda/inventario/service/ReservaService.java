package io.github.jarlh19.tienda.inventario.service;

import io.github.jarlh19.tienda.inventario.exception.ProductoNoEncontradoException;
import io.github.jarlh19.tienda.inventario.exception.ReservaYaLiberadaException;
import io.github.jarlh19.tienda.inventario.model.EstadoReserva;
import io.github.jarlh19.tienda.inventario.model.LineaReserva;
import io.github.jarlh19.tienda.inventario.model.Producto;
import io.github.jarlh19.tienda.inventario.model.Reserva;
import io.github.jarlh19.tienda.inventario.repository.ProductoRepository;
import io.github.jarlh19.tienda.inventario.repository.ReservaRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservaService {

    public record ItemSolicitado(Long productoId, int cantidad) {}

    private final ProductoRepository productos;
    private final ReservaRepository reservas;

    /**
     * Paso 1 de la saga. Es idempotente: si el orquestador reintenta con el mismo pedido, recibe la
     * misma reserva y el stock no se descuenta dos veces. Si falta stock de un producto, la
     * transacción completa se revierte y no queda nada apartado.
     */
    @Transactional
    public Reserva reservar(Long pedidoId, List<ItemSolicitado> items) {
        Optional<Reserva> existente = reservas.findByPedidoId(pedidoId);
        if (existente.isPresent()) {
            if (existente.get().getEstado() == EstadoReserva.LIBERADA) {
                throw new ReservaYaLiberadaException(pedidoId);
            }
            return existente.get();
        }

        Reserva reserva = Reserva.nueva(pedidoId);
        // Bloquear siempre en el mismo orden evita que dos pedidos se esperen mutuamente (deadlock).
        List<ItemSolicitado> ordenados = items.stream()
                .sorted(Comparator.comparing(ItemSolicitado::productoId))
                .toList();
        for (ItemSolicitado item : ordenados) {
            Producto producto = productos.buscarParaActualizar(item.productoId())
                    .orElseThrow(() -> new ProductoNoEncontradoException(item.productoId()));
            producto.descontar(item.cantidad());
            reserva.agregarLinea(producto.getId(), item.cantidad(), producto.getPrecio());
        }
        log.info("Pedido {}: stock reservado por {}", pedidoId, reserva.total());
        return reservas.save(reserva);
    }

    /**
     * Compensación del paso 1. También es idempotente, y funciona aunque la reserva nunca haya
     * llegado: en ese caso deja una marca para rechazarla si aparece después.
     */
    @Transactional
    public Reserva liberar(Long pedidoId) {
        Optional<Reserva> existente = reservas.findByPedidoId(pedidoId);
        if (existente.isEmpty()) {
            log.info("Pedido {}: se pidió liberar una reserva que no existe; queda marcada", pedidoId);
            return reservas.save(Reserva.liberadaSinReservar(pedidoId));
        }

        Reserva reserva = existente.get();
        if (reserva.getEstado() == EstadoReserva.LIBERADA) {
            return reserva;
        }
        List<LineaReserva> ordenadas = reserva.getLineas().stream()
                .sorted(Comparator.comparing(LineaReserva::getProductoId))
                .toList();
        for (LineaReserva linea : ordenadas) {
            productos.buscarParaActualizar(linea.getProductoId())
                    .orElseThrow(() -> new ProductoNoEncontradoException(linea.getProductoId()))
                    .reponer(linea.getCantidad());
        }
        reserva.liberar();
        log.info("Pedido {}: stock liberado", pedidoId);
        return reserva;
    }

    @Transactional(readOnly = true)
    public Optional<Reserva> buscar(Long pedidoId) {
        return reservas.findByPedidoId(pedidoId);
    }
}
