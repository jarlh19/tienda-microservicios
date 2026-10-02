package io.github.jarlh19.tienda.pagos.repository;

import io.github.jarlh19.tienda.pagos.model.Pago;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    Optional<Pago> findByPedidoId(Long pedidoId);
}
