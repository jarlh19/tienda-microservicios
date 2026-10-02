package io.github.jarlh19.tienda.gateway;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/** Prueba las rutas reales de config-repo/gateway.yml contra un inventario falso. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class EnrutamientoTest {

    static HttpServer inventarioFalso;

    @DynamicPropertySource
    static void levantarInventarioFalso(DynamicPropertyRegistry registro) throws IOException {
        inventarioFalso = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        // Responde 200 a cualquier ruta de inventario: si el gateway devuelve 404, es porque no reenvió.
        inventarioFalso.createContext("/api/inventario", intercambio -> {
            byte[] cuerpo = "[{\"id\":1,\"nombre\":\"Laptop\"}]".getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(200, cuerpo.length);
            intercambio.getResponseBody().write(cuerpo);
            intercambio.close();
        });
        inventarioFalso.start();
        registro.add("INVENTARIO_URL", () -> "http://localhost:" + inventarioFalso.getAddress().getPort());
    }

    @AfterAll
    static void apagar() {
        inventarioFalso.stop(0);
    }

    @Autowired
    WebTestClient cliente;

    @Test
    void reenviaLaPeticionAlServicioDeInventario() {
        cliente.get().uri("/api/inventario/productos")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].nombre").isEqualTo("Laptop");
    }

    @Test
    void lasOperacionesDeLaSagaNoSePublican() {
        cliente.post().uri("/api/inventario/reservas/1/liberar").exchange().expectStatus().isNotFound();
    }

    @Test
    void unaRutaQueNoExisteDevuelve404() {
        cliente.get().uri("/api/desconocido").exchange().expectStatus().isNotFound();
    }
}
