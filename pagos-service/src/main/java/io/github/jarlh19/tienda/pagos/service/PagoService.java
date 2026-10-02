package io.github.jarlh19.tienda.pagos.service;

import io.github.jarlh19.tienda.pagos.config.PagosProperties;
import io.github.jarlh19.tienda.pagos.model.Pago;
import io.github.jarlh19.tienda.pagos.repository.PagoRepository;
import java.math.BigDecimal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PagoService {

    private final PagoRepository pagos;
    private final PagosProperties propiedades;

    /**
     * Paso 2 de la saga. Si el pedido ya tiene un pago, se devuelve ese mismo: un reintento del
     * orquestador nunca cobra dos veces. Un rechazo también queda guardado, como en una pasarela real.
     */
    @Transactional
    public Pago cobrar(Long pedidoId, BigDecimal monto) {
        Optional<Pago> existente = pagos.findByPedidoId(pedidoId);
        if (existente.isPresent()) {
            return existente.get();
        }

        BigDecimal limite = propiedades.limiteAprobacion();
        Pago pago = monto.compareTo(limite) <= 0
                ? Pago.aprobado(pedidoId, monto)
                : Pago.rechazado(pedidoId, monto, "Fondos insuficientes: el monto supera el límite de " + limite);
        log.info("Pedido {}: pago {} por {}", pedidoId, pago.getEstado(), monto);
        return pagos.save(pago);
    }

    /** Compensación del paso 2. Idempotente, y deja una marca si el cobro todavía no llegó. */
    @Transactional
    public Pago reembolsar(Long pedidoId) {
        Optional<Pago> existente = pagos.findByPedidoId(pedidoId);
        if (existente.isEmpty()) {
            log.info("Pedido {}: reembolso de un cobro que no existe; queda anulado", pedidoId);
            return pagos.save(Pago.anuladoSinCobrar(pedidoId));
        }
        Pago pago = existente.get();
        pago.reembolsar();
        log.info("Pedido {}: pago en estado {}", pedidoId, pago.getEstado());
        return pago;
    }

    @Transactional(readOnly = true)
    public Optional<Pago> buscar(Long pedidoId) {
        return pagos.findByPedidoId(pedidoId);
    }
}
