package io.github.jarlh19.tienda.pedidos.client;

import io.github.jarlh19.tienda.pedidos.model.LineaPedido;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.math.BigDecimal;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Llamadas a inventario-service protegidas por el circuit breaker "inventario". Si inventario falla
 * seguido, el circuito se abre y las llamadas siguientes fallan al instante, sin esperar el timeout.
 */
@Slf4j
@Component
public class InventarioClient {

    record ItemReserva(Long productoId, int cantidad) {}

    record SolicitudReserva(Long pedidoId, List<ItemReserva> items) {}

    record RespuestaReserva(Long pedidoId, String estado, BigDecimal total) {}

    private final RestClient http;

    public InventarioClient(@Qualifier("inventarioRestClient") RestClient http) {
        this.http = http;
    }

    @CircuitBreaker(name = "inventario", fallbackMethod = "reservaNoDisponible")
    public ResultadoReserva reservar(Long pedidoId, List<LineaPedido> lineas) {
        List<ItemReserva> items = lineas.stream()
                .map(linea -> new ItemReserva(linea.getProductoId(), linea.getCantidad()))
                .toList();
        try {
            RespuestaReserva respuesta = http.post()
                    .uri("/api/inventario/reservas")
                    .body(new SolicitudReserva(pedidoId, items))
                    .retrieve()
                    .body(RespuestaReserva.class);
            if (respuesta == null || respuesta.total() == null) {
                throw new IllegalStateException("inventario respondió la reserva sin total");
            }
            return new ResultadoReserva.Reservada(respuesta.total());
        } catch (HttpClientErrorException error) {
            // Un rechazo de negocio (sin stock) significa que inventario funciona. Se atrapa aquí para
            // que el circuit breaker lo cuente como llamada exitosa y no abra el circuito.
            if (ErroresHttp.esRechazoDeNegocio(error)) {
                return new ResultadoReserva.Rechazada(ErroresHttp.motivo(error));
            }
            throw error;
        }
    }

    ResultadoReserva reservaNoDisponible(Long pedidoId, List<LineaPedido> lineas, Throwable causa) {
        ErroresHttp.registrar(log, pedidoId, "reservar el stock", causa);
        return new ResultadoReserva.NoDisponible(ErroresHttp.describir(causa), ErroresHttp.llamadaEnviada(causa));
    }

    /** Compensación: devuelve el stock. Responde false si no se pudo, para reintentarlo después. */
    @CircuitBreaker(name = "inventario", fallbackMethod = "liberacionNoDisponible")
    public boolean liberar(Long pedidoId) {
        http.post()
                .uri("/api/inventario/reservas/{pedidoId}/liberar", pedidoId)
                .retrieve()
                .toBodilessEntity();
        return true;
    }

    boolean liberacionNoDisponible(Long pedidoId, Throwable causa) {
        ErroresHttp.registrar(log, pedidoId, "liberar el stock", causa);
        return false;
    }
}
