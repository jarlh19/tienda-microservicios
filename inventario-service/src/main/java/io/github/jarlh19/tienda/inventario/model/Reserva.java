package io.github.jarlh19.tienda.inventario.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Stock apartado para un pedido. Hay como máximo una reserva por pedido: así reservar y liberar
 * son idempotentes y el orquestador puede reintentarlos sin descontar ni reponer dos veces.
 */
@Entity
@Table(name = "reservas")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long pedidoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoReserva estado;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "reserva_lineas", joinColumns = @JoinColumn(name = "reserva_id"))
    private List<LineaReserva> lineas = new ArrayList<>();

    @Column(nullable = false)
    private Instant actualizadaEn;

    public static Reserva nueva(Long pedidoId) {
        Reserva reserva = new Reserva();
        reserva.pedidoId = pedidoId;
        reserva.estado = EstadoReserva.RESERVADA;
        return reserva;
    }

    /**
     * La compensación llegó antes que la reserva (por ejemplo, la reserva se demoró y el orquestador
     * se rindió). Se deja registrado para rechazar esa reserva si aparece tarde.
     */
    public static Reserva liberadaSinReservar(Long pedidoId) {
        Reserva reserva = new Reserva();
        reserva.pedidoId = pedidoId;
        reserva.estado = EstadoReserva.LIBERADA;
        return reserva;
    }

    public void agregarLinea(Long productoId, int cantidad, BigDecimal precioUnitario) {
        lineas.add(new LineaReserva(productoId, cantidad, precioUnitario));
    }

    public void liberar() {
        estado = EstadoReserva.LIBERADA;
    }

    public BigDecimal total() {
        return lineas.stream().map(LineaReserva::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @PrePersist
    @PreUpdate
    void marcarActualizacion() {
        actualizadaEn = Instant.now();
    }
}
