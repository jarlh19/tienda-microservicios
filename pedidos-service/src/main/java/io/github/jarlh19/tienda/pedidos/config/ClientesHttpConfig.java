package io.github.jarlh19.tienda.pedidos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Un RestClient por servicio, con timeouts. Sin timeout, un servicio lento deja colgado al que lo
 * llama y el circuit breaker nunca se entera de que algo anda mal.
 */
@Configuration
public class ClientesHttpConfig {

    @Bean
    RestClient inventarioRestClient(RestClient.Builder builder, ServiciosProperties servicios) {
        return builder.baseUrl(servicios.inventario().url())
                .requestFactory(conTimeouts(servicios.timeout()))
                .build();
    }

    @Bean
    RestClient pagosRestClient(RestClient.Builder builder, ServiciosProperties servicios) {
        return builder.baseUrl(servicios.pagos().url())
                .requestFactory(conTimeouts(servicios.timeout()))
                .build();
    }

    private static ClientHttpRequestFactory conTimeouts(ServiciosProperties.Timeout timeout) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(timeout.conexion());
        fabrica.setReadTimeout(timeout.lectura());
        return fabrica;
    }
}
