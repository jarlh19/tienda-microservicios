package io.github.jarlh19.tienda.pedidos.service;

import io.github.jarlh19.tienda.pedidos.config.SagaProperties;
import io.github.jarlh19.tienda.pedidos.model.EstadoPedido;
import io.github.jarlh19.tienda.pedidos.model.Pedido;
import io.github.jarlh19.tienda.pedidos.repository.PedidoRepository;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Proceso en segundo plano que garantiza que toda saga termine:
 * <ul>
 *   <li>reintenta las compensaciones que fallaron porque un servicio estaba caído;
 *   <li>compensa las sagas que quedaron a medias, por ejemplo si pedidos-service se reinició entre
 *       un paso y otro.
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "saga.reintentos-automaticos", havingValue = "true", matchIfMissing = true)
public class ReintentosSaga {

    private final PedidoRepository pedidos;
    private final SagaPedido saga;
    private final SagaProperties propiedades;

    @Scheduled(fixedDelayString = "${saga.reintento-cada}", initialDelayString = "${saga.reintento-cada}")
    public void ejecutar() {
        // Cada fase por separado: si una falla, la otra corre igual.
        try {
            compensarSagasInterrumpidas();
        } catch (RuntimeException e) {
            log.error("No se pudieron buscar las sagas interrumpidas", e);
        }
        try {
            reintentarCompensaciones();
        } catch (RuntimeException e) {
            log.error("No se pudieron buscar las compensaciones pendientes", e);
        }
    }

    void compensarSagasInterrumpidas() {
        Instant limite = Instant.now().minus(propiedades.pedidoColgadoTras());
        List<Pedido> colgados = pedidos.findByEstadoInAndActualizadoEnBefore(
                List.of(EstadoPedido.PENDIENTE, EstadoPedido.STOCK_RESERVADO), limite);
        for (Pedido pedido : colgados) {
            procesar(pedido, "compensar la saga interrumpida", p -> {
                log.warn("Pedido {}: lleva en {} desde {}; se compensa", p.getId(), p.getEstado(), p.getActualizadoEn());
                p.marcarInterrumpida();
                saga.compensar(pedidos.save(p));
            });
        }
    }

    /**
     * Solo toma los pedidos que llevan un rato sin moverse: uno recién pasado a COMPENSANDO puede
     * estar compensándose todavía dentro de la petición que lo creó.
     */
    void reintentarCompensaciones() {
        Instant limite = Instant.now().minus(propiedades.reintentoCada());
        for (Pedido pedido : pedidos.findByEstadoAndActualizadoEnBefore(EstadoPedido.COMPENSANDO, limite)) {
            procesar(pedido, "reintentar la compensación", saga::compensar);
        }
    }

    private void procesar(Pedido pedido, String accion, Consumer<Pedido> paso) {
        try {
            paso.accept(pedido);
        } catch (ObjectOptimisticLockingFailureException e) {
            // Otro proceso lo modificó a la vez. Las compensaciones son idempotentes: no se perdió nada.
            log.info("Pedido {}: otro proceso lo actualizó mientras se intentaba {}; queda para la próxima vuelta",
                    pedido.getId(), accion);
        } catch (RuntimeException e) {
            log.error("Pedido {}: falló al {}", pedido.getId(), accion, e);
        }
    }
}
