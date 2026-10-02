package io.github.jarlh19.tienda.pedidos.model;

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
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * El pedido guarda el estado de su saga. Las transiciones solo se hacen con estos métodos, que
 * validan desde qué estado se puede llegar a cada uno.
 */
@Entity
@Table(name = "pedidos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pedido {

    private static final int LARGO_MOTIVO = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Evita que la saga y el proceso de reintentos pisen el mismo pedido a la vez. */
    @Version
    private long version;

    @Column(nullable = false, length = 100)
    private String cliente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPedido estado;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "pedido_lineas", joinColumns = @JoinColumn(name = "pedido_id"))
    private List<LineaPedido> lineas = new ArrayList<>();

    @Column(precision = 12, scale = 2)
    private BigDecimal total;

    @Column(length = LARGO_MOTIVO)
    private String motivo;

    /** Compensaciones que faltan. Quedan guardadas para reintentarlas si el otro servicio está caído. */
    private boolean liberarStockPendiente;

    private boolean reembolsoPendiente;

    /** Cuántas veces se intentó compensar sin terminar. Sirve para alertar si no avanza. */
    private int intentosCompensacion;

    @Column(nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(nullable = false)
    private Instant actualizadoEn;

    public static Pedido nuevo(String cliente, List<LineaPedido> lineas) {
        Pedido pedido = new Pedido();
        pedido.cliente = cliente;
        pedido.lineas = new ArrayList<>(lineas);
        pedido.estado = EstadoPedido.PENDIENTE;
        return pedido;
    }

    public void stockReservado(BigDecimal total) {
        exigirEstado(EstadoPedido.PENDIENTE);
        this.total = total;
        estado = EstadoPedido.STOCK_RESERVADO;
    }

    public void confirmar() {
        exigirEstado(EstadoPedido.STOCK_RESERVADO);
        estado = EstadoPedido.CONFIRMADO;
    }

    /** Cancelación directa: no se hizo ningún paso, así que no hay nada que deshacer. */
    public void cancelar(String motivo) {
        exigirEstado(EstadoPedido.PENDIENTE);
        this.motivo = recortar(motivo);
        estado = EstadoPedido.CANCELADO;
    }

    /** Algo falló a mitad de camino: hay que deshacer los pasos indicados antes de cancelar. */
    public void compensar(String motivo, boolean liberarStock, boolean reembolsar) {
        if (estado == EstadoPedido.CONFIRMADO || estado == EstadoPedido.CANCELADO) {
            throw new IllegalStateException("El pedido " + id + " ya terminó en " + estado);
        }
        this.motivo = recortar(motivo);
        liberarStockPendiente |= liberarStock;
        reembolsoPendiente |= reembolsar;
        estado = EstadoPedido.COMPENSANDO;
        cancelarSiNoQuedaNada();
    }

    /** La saga quedó a medias (por ejemplo, el servicio se reinició): se deshace lo que pudo haberse hecho. */
    public void marcarInterrumpida() {
        String detalle = "SAGA_INTERRUMPIDA: el pedido quedó en " + estado + " sin avanzar";
        switch (estado) {
            case PENDIENTE -> compensar(detalle, true, false);
            case STOCK_RESERVADO -> compensar(detalle, true, true);
            default -> throw new IllegalStateException("El pedido " + id + " no está a mitad de la saga: " + estado);
        }
    }

    public void stockLiberado() {
        liberarStockPendiente = false;
        cancelarSiNoQuedaNada();
    }

    public void pagoReembolsado() {
        reembolsoPendiente = false;
        cancelarSiNoQuedaNada();
    }

    /** @return el total de intentos fallidos hasta ahora */
    public int registrarIntentoFallido() {
        return ++intentosCompensacion;
    }

    public List<String> compensacionesPendientes() {
        List<String> pendientes = new ArrayList<>();
        if (reembolsoPendiente) {
            pendientes.add("REEMBOLSAR_PAGO");
        }
        if (liberarStockPendiente) {
            pendientes.add("LIBERAR_STOCK");
        }
        return pendientes;
    }

    private void cancelarSiNoQuedaNada() {
        if (estado == EstadoPedido.COMPENSANDO && !liberarStockPendiente && !reembolsoPendiente) {
            estado = EstadoPedido.CANCELADO;
        }
    }

    /** El motivo trae texto de otros servicios (por ejemplo, una página de error) y la columna tiene tope. */
    private static String recortar(String motivo) {
        if (motivo == null || motivo.length() <= LARGO_MOTIVO) {
            return motivo;
        }
        return motivo.substring(0, LARGO_MOTIVO - 1) + "…";
    }

    private void exigirEstado(EstadoPedido esperado) {
        if (estado != esperado) {
            throw new IllegalStateException(
                    "El pedido " + id + " está en " + estado + " y se esperaba " + esperado);
        }
    }

    @PrePersist
    void alCrear() {
        creadoEn = Instant.now();
        actualizadoEn = creadoEn;
    }

    @PreUpdate
    void alActualizar() {
        actualizadoEn = Instant.now();
    }
}
