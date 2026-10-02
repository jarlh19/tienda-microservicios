package io.github.jarlh19.tienda.pedidos.dto;

import io.github.jarlh19.tienda.pedidos.model.LineaPedido;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CrearPedidoRequest(
        @NotBlank @Size(max = 100) String cliente,
        @NotEmpty List<@Valid Linea> lineas) {

    public record Linea(@NotNull Long productoId, @Positive int cantidad) {}

    public List<LineaPedido> lineasDelPedido() {
        return lineas.stream().map(linea -> new LineaPedido(linea.productoId(), linea.cantidad())).toList();
    }
}
