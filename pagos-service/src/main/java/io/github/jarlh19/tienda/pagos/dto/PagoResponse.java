package io.github.jarlh19.tienda.pagos.dto;

import io.github.jarlh19.tienda.pagos.model.EstadoPago;
import io.github.jarlh19.tienda.pagos.model.Pago;
import java.math.BigDecimal;

public record PagoResponse(Long pedidoId, BigDecimal monto, EstadoPago estado, String motivo) {

    public static PagoResponse de(Pago pago) {
        return new PagoResponse(pago.getPedidoId(), pago.getMonto(), pago.getEstado(), pago.getMotivo());
    }
}
