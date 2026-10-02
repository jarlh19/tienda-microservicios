package io.github.jarlh19.tienda.inventario.repository;

import io.github.jarlh19.tienda.inventario.model.Reserva;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    Optional<Reserva> findByPedidoId(Long pedidoId);
}
