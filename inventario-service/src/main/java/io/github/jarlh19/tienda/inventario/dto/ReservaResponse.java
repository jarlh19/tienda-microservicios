package io.github.jarlh19.tienda.inventario.dto;

import io.github.jarlh19.tienda.inventario.model.EstadoReserva;
import io.github.jarlh19.tienda.inventario.model.Reserva;
import java.math.BigDecimal;
import java.util.List;

public record ReservaResponse(Long pedidoId, EstadoReserva estado, BigDecimal total, List<Linea> lineas) {

    public record Linea(Long productoId, int cantidad, BigDecimal precioUnitario) {}

    public static ReservaResponse de(Reserva reserva) {
        List<Linea> lineas = reserva.getLineas().stream()
                .map(l -> new Linea(l.getProductoId(), l.getCantidad(), l.getPrecioUnitario()))
                .toList();
        return new ReservaResponse(reserva.getPedidoId(), reserva.getEstado(), reserva.total(), lineas);
    }
}
