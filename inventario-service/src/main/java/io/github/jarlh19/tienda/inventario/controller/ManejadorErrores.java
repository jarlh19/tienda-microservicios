package io.github.jarlh19.tienda.inventario.controller;

import io.github.jarlh19.tienda.inventario.exception.ProductoNoEncontradoException;
import io.github.jarlh19.tienda.inventario.exception.ReservaYaLiberadaException;
import io.github.jarlh19.tienda.inventario.exception.StockInsuficienteException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Los errores salen en formato RFC 7807 (ProblemDetail) con un campo "codigo" que el orquestador
 * de pedidos guarda como motivo de la cancelación.
 */
@RestControllerAdvice
public class ManejadorErrores {

    @ExceptionHandler(StockInsuficienteException.class)
    ProblemDetail stockInsuficiente(StockInsuficienteException e) {
        return problema(HttpStatus.CONFLICT, "STOCK_INSUFICIENTE", e.getMessage());
    }

    @ExceptionHandler(ReservaYaLiberadaException.class)
    ProblemDetail reservaYaLiberada(ReservaYaLiberadaException e) {
        return problema(HttpStatus.CONFLICT, "RESERVA_YA_LIBERADA", e.getMessage());
    }

    @ExceptionHandler(ProductoNoEncontradoException.class)
    ProblemDetail productoNoEncontrado(ProductoNoEncontradoException e) {
        return problema(HttpStatus.NOT_FOUND, "PRODUCTO_NO_EXISTE", e.getMessage());
    }

    private static ProblemDetail problema(HttpStatus estado, String codigo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setProperty("codigo", codigo);
        return problema;
    }
}
