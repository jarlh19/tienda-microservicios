package io.github.jarlh19.tienda.pedidos.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/** Llamadas a pagos-service protegidas por el circuit breaker "pagos". */
@Slf4j
@Component
public class PagosClient {

    record SolicitudCobro(Long pedidoId, BigDecimal monto) {}

    record RespuestaPago(Long pedidoId, BigDecimal monto, String estado, String motivo) {}

    private final RestClient http;

    public PagosClient(@Qualifier("pagosRestClient") RestClient http) {
        this.http = http;
    }

    @CircuitBreaker(name = "pagos", fallbackMethod = "cobroNoDisponible")
    public ResultadoPago cobrar(Long pedidoId, BigDecimal monto) {
        try {
            RespuestaPago respuesta = http.post()
                    .uri("/api/pagos")
                    .body(new SolicitudCobro(pedidoId, monto))
                    .retrieve()
                    .body(RespuestaPago.class);
            if (respuesta == null || respuesta.estado() == null) {
                throw new IllegalStateException("pagos respondió el cobro sin estado");
            }
            if ("APROBADO".equals(respuesta.estado())) {
                return new ResultadoPago.Aprobado();
            }
            String detalle = respuesta.motivo() == null ? "" : ": " + respuesta.motivo();
            return new ResultadoPago.Rechazado("PAGO_" + respuesta.estado() + detalle);
        } catch (HttpClientErrorException error) {
            if (ErroresHttp.esRechazoDeNegocio(error)) {
                return new ResultadoPago.Rechazado(ErroresHttp.motivo(error));
            }
            throw error;
        }
    }

    ResultadoPago cobroNoDisponible(Long pedidoId, BigDecimal monto, Throwable causa) {
        ErroresHttp.registrar(log, pedidoId, "cobrar", causa);
        return new ResultadoPago.NoDisponible(ErroresHttp.describir(causa), ErroresHttp.llamadaEnviada(causa));
    }

    /** Compensación: devuelve el dinero, o anula el cobro si todavía no llegó. False si no se pudo. */
    @CircuitBreaker(name = "pagos", fallbackMethod = "reembolsoNoDisponible")
    public boolean reembolsar(Long pedidoId) {
        http.post()
                .uri("/api/pagos/{pedidoId}/reembolso", pedidoId)
                .retrieve()
                .toBodilessEntity();
        return true;
    }

    boolean reembolsoNoDisponible(Long pedidoId, Throwable causa) {
        ErroresHttp.registrar(log, pedidoId, "reembolsar", causa);
        return false;
    }
}
