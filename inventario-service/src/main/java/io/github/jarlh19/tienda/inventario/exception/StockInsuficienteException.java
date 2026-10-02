package io.github.jarlh19.tienda.inventario.exception;

public class StockInsuficienteException extends RuntimeException {

    public StockInsuficienteException(Long productoId, String nombre, int solicitado, int disponible) {
        super("Stock insuficiente de '%s' (id %d): se pidieron %d y hay %d"
                .formatted(nombre, productoId, solicitado, disponible));
    }
}
