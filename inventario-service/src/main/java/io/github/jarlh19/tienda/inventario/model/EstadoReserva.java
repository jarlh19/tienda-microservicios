package io.github.jarlh19.tienda.inventario.model;

public enum EstadoReserva {
    RESERVADA,
    /** El stock volvió al inventario porque la saga del pedido se compensó. */
    LIBERADA
}
