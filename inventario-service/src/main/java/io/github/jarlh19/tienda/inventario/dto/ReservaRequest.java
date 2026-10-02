package io.github.jarlh19.tienda.inventario.dto;

import io.github.jarlh19.tienda.inventario.service.ReservaService.ItemSolicitado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record ReservaRequest(@NotNull Long pedidoId, @NotEmpty List<@Valid Item> items) {

    public record Item(@NotNull Long productoId, @Positive int cantidad) {}

    public List<ItemSolicitado> itemsSolicitados() {
        return items.stream().map(item -> new ItemSolicitado(item.productoId(), item.cantidad())).toList();
    }
}
