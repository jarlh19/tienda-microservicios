package io.github.jarlh19.tienda.pedidos.controller;

import io.github.jarlh19.tienda.pedidos.exception.PedidoNoEncontradoException;
import io.github.jarlh19.tienda.pedidos.exception.SagaInterrumpidaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ManejadorErrores {

    @ExceptionHandler(PedidoNoEncontradoException.class)
    ProblemDetail pedidoNoEncontrado(PedidoNoEncontradoException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problema.setProperty("codigo", "PEDIDO_NO_EXISTE");
        return problema;
    }

    /** Incluye el id del pedido: el cliente debe consultarlo, no reintentar el POST. */
    @ExceptionHandler(SagaInterrumpidaException.class)
    ProblemDetail sagaInterrumpida(SagaInterrumpidaException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "El pedido %d quedó registrado pero su saga se interrumpió; si quedó a medias, se compensará automáticamente. Consulta GET /api/pedidos/%d."
                        .formatted(e.getPedidoId(), e.getPedidoId()));
        problema.setProperty("codigo", "SAGA_INTERRUMPIDA");
        problema.setProperty("pedidoId", e.getPedidoId());
        return problema;
    }
}
