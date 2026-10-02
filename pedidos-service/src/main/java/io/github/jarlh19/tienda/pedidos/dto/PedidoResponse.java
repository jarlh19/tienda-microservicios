package io.github.jarlh19.tienda.pedidos.dto;

import io.github.jarlh19.tienda.pedidos.model.EstadoPedido;
import io.github.jarlh19.tienda.pedidos.model.Pedido;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PedidoResponse(
        Long id,
        String cliente,
        EstadoPedido estado,
        BigDecimal total,
        String motivo,
        List<String> compensacionesPendientes,
        int intentosCompensacion,
        List<Linea> lineas,
        Instant creadoEn,
        Instant actualizadoEn) {

    public record Linea(Long productoId, int cantidad) {}

    public static PedidoResponse de(Pedido pedido) {
        List<Linea> lineas = pedido.getLineas().stream()
                .map(linea -> new Linea(linea.getProductoId(), linea.getCantidad()))
                .toList();
        return new PedidoResponse(pedido.getId(), pedido.getCliente(), pedido.getEstado(), pedido.getTotal(),
                pedido.getMotivo(), pedido.compensacionesPendientes(), pedido.getIntentosCompensacion(), lineas,
                pedido.getCreadoEn(), pedido.getActualizadoEn());
    }
}
