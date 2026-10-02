package io.github.jarlh19.tienda.pedidos.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.github.jarlh19.tienda.pedidos.model.LineaPedido;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * El circuit breaker real contra un inventario falso al que se le cambia la respuesta: sano, con
 * error de negocio, caído o lento.
 */
@SpringBootTest
class CircuitBreakerTest {

    record Respuesta(int estado, String cuerpo, long demoraMs) {}

    static final Respuesta RESERVA_OK = new Respuesta(201, "{\"pedidoId\":1,\"estado\":\"RESERVADA\",\"total\":91.80}", 0);
    static final Respuesta SIN_STOCK = new Respuesta(409,
            "{\"status\":409,\"codigo\":\"STOCK_INSUFICIENTE\",\"detail\":\"Stock insuficiente de 'Laptop'\"}", 0);
    /** Un 400 sin "codigo": por ejemplo, inventario cambió el nombre de un campo. */
    static final Respuesta CONTRATO_ROTO = new Respuesta(400, "{\"status\":400,\"detail\":\"Invalid request content.\"}", 0);
    static final Respuesta ERROR_INTERNO = new Respuesta(500, "{\"status\":500}", 0);
    static final Respuesta LENTA = new Respuesta(201, RESERVA_OK.cuerpo(), 1_500);

    static final List<LineaPedido> LINEAS = List.of(new LineaPedido(1L, 2));

    static HttpServer inventarioFalso;
    static final AtomicReference<Respuesta> respuesta = new AtomicReference<>(RESERVA_OK);
    static final AtomicInteger peticionesRecibidas = new AtomicInteger();

    @DynamicPropertySource
    static void levantarInventarioFalso(DynamicPropertyRegistry registro) throws IOException {
        inventarioFalso = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        inventarioFalso.createContext("/api/inventario/reservas", intercambio -> {
            peticionesRecibidas.incrementAndGet();
            Respuesta r = respuesta.get();
            try {
                Thread.sleep(r.demoraMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] cuerpo = r.cuerpo().getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(r.estado(), cuerpo.length);
            intercambio.getResponseBody().write(cuerpo);
            intercambio.close();
        });
        inventarioFalso.setExecutor(Executors.newCachedThreadPool());
        inventarioFalso.start();
        registro.add("servicios.inventario.url", () -> "http://localhost:" + inventarioFalso.getAddress().getPort());
    }

    @AfterAll
    static void apagar() {
        inventarioFalso.stop(0);
    }

    @Autowired
    InventarioClient inventario;

    @Autowired
    CircuitBreakerRegistry circuitos;

    CircuitBreaker circuito;

    @BeforeEach
    void circuitoCerrado() {
        circuito = circuitos.circuitBreaker("inventario");
        circuito.reset();
        peticionesRecibidas.set(0);
    }

    @Test
    void conInventarioSanoReservaYElCircuitoSigueCerrado() {
        respuesta.set(RESERVA_OK);

        ResultadoReserva resultado = inventario.reservar(1L, LINEAS);

        assertThat(resultado).isInstanceOf(ResultadoReserva.Reservada.class);
        assertThat(circuito.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void laFaltaDeStockNoAbreElCircuitoPorqueNoEsUnaFalla() {
        respuesta.set(SIN_STOCK);

        for (int i = 0; i < 5; i++) {
            assertThat(inventario.reservar(1L, LINEAS))
                    .isEqualTo(new ResultadoReserva.Rechazada("STOCK_INSUFICIENTE: Stock insuficiente de 'Laptop'"));
        }

        assertThat(circuito.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void unContratoRotoNoSeConfundeConFaltaDeStockNiAbreElCircuito() {
        respuesta.set(CONTRATO_ROTO);

        ResultadoReserva resultado = inventario.reservar(1L, LINEAS);

        assertThat(resultado).isInstanceOfSatisfying(ResultadoReserva.NoDisponible.class,
                caida -> assertThat(caida.detalle()).contains("400"));
        assertThat(circuito.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(circuito.getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    void tresErroresSeguidosAbrenElCircuitoYLaSiguienteLlamadaNiSale() {
        respuesta.set(ERROR_INTERNO);

        for (int i = 0; i < 3; i++) {
            ResultadoReserva resultado = inventario.reservar(1L, LINEAS);
            assertThat(resultado).isInstanceOfSatisfying(ResultadoReserva.NoDisponible.class,
                    caida -> assertThat(caida.llamadaEnviada()).isTrue());
        }
        assertThat(circuito.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        ResultadoReserva conCircuitoAbierto = inventario.reservar(1L, LINEAS);

        assertThat(conCircuitoAbierto).isInstanceOfSatisfying(ResultadoReserva.NoDisponible.class,
                caida -> assertThat(caida.llamadaEnviada()).isFalse());
        assertThat(peticionesRecibidas).hasValue(3);
    }

    @Test
    void unaRespuestaMasLentaQueElTimeoutCuentaComoFalla() {
        respuesta.set(LENTA);

        ResultadoReserva resultado = inventario.reservar(1L, LINEAS);

        assertThat(resultado).isInstanceOfSatisfying(ResultadoReserva.NoDisponible.class,
                caida -> assertThat(caida.detalle()).containsIgnoringCase("timed out"));
        assertThat(circuito.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }
}
