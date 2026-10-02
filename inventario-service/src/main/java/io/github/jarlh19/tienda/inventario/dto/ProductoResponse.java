package io.github.jarlh19.tienda.inventario.dto;

import io.github.jarlh19.tienda.inventario.model.Producto;
import java.math.BigDecimal;

public record ProductoResponse(Long id, String nombre, BigDecimal precio, int stock) {

    public static ProductoResponse de(Producto producto) {
        return new ProductoResponse(producto.getId(), producto.getNombre(), producto.getPrecio(), producto.getStock());
    }
}
