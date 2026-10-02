package io.github.jarlh19.tienda.pagos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Un pago por pedido: cobrar y reembolsar son idempotentes. */
@Entity
@Table(name = "pagos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long pedidoId;

    @Column(precision = 12, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPago estado;

    @Column(length = 300)
    private String motivo;

    @Column(nullable = false)
    private Instant actualizadoEn;

    private Pago(Long pedidoId, BigDecimal monto, EstadoPago estado, String motivo) {
        this.pedidoId = pedidoId;
        this.monto = monto;
        this.estado = estado;
        this.motivo = motivo;
    }

    public static Pago aprobado(Long pedidoId, BigDecimal monto) {
        return new Pago(pedidoId, monto, EstadoPago.APROBADO, null);
    }

    public static Pago rechazado(Long pedidoId, BigDecimal monto, String motivo) {
        return new Pago(pedidoId, monto, EstadoPago.RECHAZADO, motivo);
    }

    public static Pago anuladoSinCobrar(Long pedidoId) {
        return new Pago(pedidoId, null, EstadoPago.ANULADO, "Compensado antes de recibir el cobro");
    }

    public void reembolsar() {
        if (estado == EstadoPago.APROBADO) {
            estado = EstadoPago.REEMBOLSADO;
        }
    }

    @PrePersist
    @PreUpdate
    void marcarActualizacion() {
        actualizadoEn = Instant.now();
    }
}
