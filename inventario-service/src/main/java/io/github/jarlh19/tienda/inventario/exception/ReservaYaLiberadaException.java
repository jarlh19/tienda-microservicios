package io.github.jarlh19.tienda.inventario.exception;

public class ReservaYaLiberadaException extends RuntimeException {

    public ReservaYaLiberadaException(Long pedidoId) {
        super("El pedido " + pedidoId + " ya fue compensado: no se puede reservar stock para él");
    }
}
