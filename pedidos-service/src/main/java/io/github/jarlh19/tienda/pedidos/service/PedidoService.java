package io.github.jarlh19.tienda.pedidos.service;

import io.github.jarlh19.tienda.pedidos.exception.PedidoNoEncontradoException;
import io.github.jarlh19.tienda.pedidos.exception.SagaInterrumpidaException;
import io.github.jarlh19.tienda.pedidos.model.LineaPedido;
import io.github.jarlh19.tienda.pedidos.model.Pedido;
import io.github.jarlh19.tienda.pedidos.repository.PedidoRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PedidoService {

    private final PedidoRepository pedidos;
    private final SagaPedido saga;

    /** Registra el pedido y ejecuta la saga. Devuelve el pedido en su estado final. */
    public Pedido crear(String cliente, List<LineaPedido> lineas) {
        Pedido pedido = pedidos.save(Pedido.nuevo(cliente, lineas));
        try {
            return saga.ejecutar(pedido);
        } catch (RuntimeException e) {
            // El pedido ya está guardado: hay que devolver su id para que el cliente consulte en
            // vez de reintentar y crear un duplicado.
            log.error("Pedido {}: la saga se interrumpió por un error inesperado", pedido.getId(), e);
            throw new SagaInterrumpidaException(pedido.getId(), e);
        }
    }

    @Transactional(readOnly = true)
    public Pedido buscar(Long id) {
        return pedidos.findById(id).orElseThrow(() -> new PedidoNoEncontradoException(id));
    }

    @Transactional(readOnly = true)
    public List<Pedido> listar() {
        return pedidos.findAll(Sort.by(Sort.Direction.DESC, "id"));
    }
}
