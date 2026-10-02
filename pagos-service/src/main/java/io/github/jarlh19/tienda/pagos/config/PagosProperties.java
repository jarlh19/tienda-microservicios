package io.github.jarlh19.tienda.pagos.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** @param limiteAprobacion monto máximo que se aprueba; uno mayor se rechaza por fondos insuficientes */
@Validated
@ConfigurationProperties("pagos")
public record PagosProperties(@NotNull @Positive BigDecimal limiteAprobacion) {}
