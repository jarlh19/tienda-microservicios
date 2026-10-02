package io.github.jarlh19.tienda.pagos.model;

public enum EstadoPago {
    APROBADO,
    RECHAZADO,
    /** Se cobró y luego se devolvió el dinero porque la saga del pedido se compensó. */
    REEMBOLSADO,
    /** La compensación llegó antes que el cobro: si el cobro aparece después, no se aprueba. */
    ANULADO
}
