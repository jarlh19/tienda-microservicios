package io.github.jarlh19.tienda.pedidos.repository;

import io.github.jarlh19.tienda.pedidos.model.EstadoPedido;
import io.github.jarlh19.tienda.pedidos.model.Pedido;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    List<Pedido> findByEstadoAndActualizadoEnBefore(EstadoPedido estado, Instant limite);

    List<Pedido> findByEstadoInAndActualizadoEnBefore(Collection<EstadoPedido> estados, Instant limite);
}
