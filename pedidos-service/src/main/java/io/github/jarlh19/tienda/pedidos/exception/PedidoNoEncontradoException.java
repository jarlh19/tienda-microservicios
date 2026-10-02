package io.github.jarlh19.tienda.pedidos.exception;

public class PedidoNoEncontradoException extends RuntimeException {

    public PedidoNoEncontradoException(Long id) {
        super("No existe el pedido con id " + id);
    }
}
