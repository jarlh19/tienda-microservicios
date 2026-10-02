package io.github.jarlh19.tienda.pagos.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CobroRequest(@NotNull Long pedidoId, @NotNull @Positive BigDecimal monto) {}
