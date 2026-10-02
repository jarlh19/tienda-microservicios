package io.github.jarlh19.tienda.pedidos.exception;

import lombok.Getter;

/** La saga se cortó por un error inesperado. El pedido ya existe y ReintentosSaga lo va a compensar. */
@Getter
public class SagaInterrumpidaException extends RuntimeException {

    private final Long pedidoId;

    public SagaInterrumpidaException(Long pedidoId, Throwable causa) {
        super("La saga del pedido " + pedidoId + " se interrumpió por un error inesperado", causa);
        this.pedidoId = pedidoId;
    }
}
