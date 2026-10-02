package io.github.jarlh19.tienda.inventario.exception;

public class ProductoNoEncontradoException extends RuntimeException {

    public ProductoNoEncontradoException(Long productoId) {
        super("No existe el producto con id " + productoId);
    }
}
