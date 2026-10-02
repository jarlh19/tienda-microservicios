package io.github.jarlh19.tienda.configserver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.cloud.config.server.native.search-locations=file:../config-repo/")
class ConfigServerApplicationTest {

    @Autowired
    TestRestTemplate http;

    @Test
    @SuppressWarnings("unchecked")
    void sirveLaConfiguracionDePedidosDesdeLaCarpetaConfigRepo() {
        Map<String, Object> respuesta = http.getForObject("/pedidos-service/default", Map.class);

        assertThat(respuesta).containsEntry("name", "pedidos-service");
        assertThat(respuesta.get("propertySources").toString())
                .contains("pedidos-service.yml")
                .contains("application.yml");
    }
}
