package io.github.jarlh19.tienda.pedidos.client;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

final class ErroresHttp {

    private ErroresHttp() {}

    /**
     * Inventario y pagos marcan sus rechazos de negocio (sin stock, producto inexistente) con un campo
     * "codigo" en el ProblemDetail. Un 4xx sin ese campo no es un rechazo: es un contrato roto (una
     * ruta o un campo que cambió) y no debe pasar por "falta de stock".
     */
    static boolean esRechazoDeNegocio(HttpClientErrorException error) {
        Map<?, ?> problema = leerProblema(error);
        return problema != null && problema.get("codigo") != null;
    }

    /** Arma el motivo a partir del ProblemDetail ("codigo: detalle"). */
    static String motivo(HttpClientErrorException error) {
        Map<?, ?> problema = leerProblema(error);
        if (problema != null && problema.get("codigo") != null) {
            return problema.get("codigo") + ": " + problema.get("detail");
        }
        return "HTTP_" + error.getStatusCode().value() + ": " + error.getResponseBodyAsString();
    }

    /** Con el circuito abierto la petición no sale: es la única falla en la que se sabe que no pasó nada. */
    static boolean llamadaEnviada(Throwable causa) {
        return !(causa instanceof CallNotPermittedException);
    }

    /**
     * Describe la causa raíz: un timeout llega envuelto en otras excepciones y el mensaje de arriba
     * no lo menciona.
     */
    static String describir(Throwable causa) {
        if (causa instanceof CallNotPermittedException) {
            return "circuito abierto, la llamada no se intentó";
        }
        Throwable raiz = NestedExceptionUtils.getMostSpecificCause(causa);
        return raiz.getClass().getSimpleName() + ": " + raiz.getMessage();
    }

    /**
     * Una caída (red, timeout, 5xx, circuito abierto) se registra como WARN: es esperable y se
     * reintenta. Cualquier otra cosa, como un contrato roto o una respuesta vacía, es un bug y se
     * registra como ERROR con su stack, para que no se confunda con una caída.
     */
    static void registrar(Logger log, Long pedidoId, String operacion, Throwable causa) {
        if (esCaida(causa)) {
            log.warn("Pedido {}: no se pudo {} ({})", pedidoId, operacion, describir(causa));
        } else {
            log.error("Pedido {}: error inesperado al {} ({})", pedidoId, operacion, describir(causa), causa);
        }
    }

    private static boolean esCaida(Throwable causa) {
        return causa instanceof CallNotPermittedException
                || causa instanceof HttpServerErrorException
                || NestedExceptionUtils.getMostSpecificCause(causa) instanceof IOException;
    }

    private static Map<?, ?> leerProblema(HttpClientErrorException error) {
        try {
            return error.getResponseBodyAs(Map.class);
        } catch (RuntimeException cuerpoNoJson) {
            // El llamador usa entonces el cuerpo tal cual, así que el detalle no se pierde.
            return null;
        }
    }
}
