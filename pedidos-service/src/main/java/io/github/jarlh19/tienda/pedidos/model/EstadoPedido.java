package io.github.jarlh19.tienda.pedidos.model;

/**
 * Estados de la saga:
 *
 * <pre>
 * PENDIENTE ──reserva ok──▶ STOCK_RESERVADO ──pago ok──▶ CONFIRMADO
 *     │                          │
 *     │ sin stock / circuito     │ pago rechazado o sin respuesta
 *     ▼ abierto                  ▼
 * CANCELADO ◀──compensado── COMPENSANDO (reintenta hasta lograrlo)
 * </pre>
 */
public enum EstadoPedido {
    PENDIENTE,
    STOCK_RESERVADO,
    CONFIRMADO,
    /** Falta deshacer algún paso; se reintenta en segundo plano. */
    COMPENSANDO,
    CANCELADO
}
