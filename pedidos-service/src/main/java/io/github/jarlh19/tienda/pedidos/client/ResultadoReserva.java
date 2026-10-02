package io.github.jarlh19.tienda.pedidos.client;

import java.math.BigDecimal;

/** Lo que puede pasar al pedir la reserva de stock. El orquestador decide qué hacer con cada caso. */
public sealed interface ResultadoReserva {

    record Reservada(BigDecimal total) implements ResultadoReserva {}

    /** Inventario respondió que no (sin stock, producto inexistente). El servicio está sano. */
    record Rechazada(String motivo) implements ResultadoReserva {}

    /**
     * No hubo respuesta útil: caída, timeout, error 5xx o circuito abierto.
     *
     * @param llamadaEnviada false si el circuito estaba abierto y la petición ni salió; true si salió
     *     y no se sabe si la reserva llegó a hacerse
     */
    record NoDisponible(String detalle, boolean llamadaEnviada) implements ResultadoReserva {}
}
