package io.github.jarlh19.tienda.pedidos.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Dónde están los otros servicios y cuánto se les espera. Viene de config-repo/pedidos-service.yml. */
@Validated
@ConfigurationProperties("servicios")
public record ServiciosProperties(
        @Valid @NotNull Servicio inventario,
        @Valid @NotNull Servicio pagos,
        @Valid @NotNull Timeout timeout) {

    public record Servicio(@NotBlank String url) {}

    public record Timeout(@NotNull Duration conexion, @NotNull Duration lectura) {}
}
