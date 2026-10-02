package io.github.jarlh19.tienda.pedidos.client;

/** Lo que puede pasar al cobrar un pedido. */
public sealed interface ResultadoPago {

    record Aprobado() implements ResultadoPago {}

    /** Pagos respondió que no (fondos insuficientes, pago anulado). El servicio está sano. */
    record Rechazado(String motivo) implements ResultadoPago {}

    /**
     * No hubo respuesta útil.
     *
     * @param llamadaEnviada si es true, el cobro pudo haberse hecho aunque no llegó la respuesta
     */
    record NoDisponible(String detalle, boolean llamadaEnviada) implements ResultadoPago {}
}
