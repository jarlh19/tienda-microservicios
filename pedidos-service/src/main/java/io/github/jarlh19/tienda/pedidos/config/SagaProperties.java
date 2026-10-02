package io.github.jarlh19.tienda.pedidos.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param reintentoCada cada cuánto se reintentan las compensaciones pendientes. Una compensación
 *     solo se reintenta si lleva al menos este tiempo sin moverse, para no pisar a la petición que
 *     todavía la está ejecutando.
 * @param pedidoColgadoTras tiempo sin avanzar tras el cual una saga se da por interrumpida
 * @param alertarTrasIntentos intentos de compensación fallidos a partir de los cuales se registra
 *     un ERROR: el pedido necesita que alguien lo revise
 */
@Validated
@ConfigurationProperties("saga")
public record SagaProperties(
        @NotNull Duration reintentoCada,
        @NotNull Duration pedidoColgadoTras,
        @Positive int alertarTrasIntentos) {}
