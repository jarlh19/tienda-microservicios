package io.github.jarlh19.tienda.pedidos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.jarlh19.tienda.pedidos.client.InventarioClient;
import io.github.jarlh19.tienda.pedidos.client.PagosClient;
import io.github.jarlh19.tienda.pedidos.client.ResultadoPago;
import io.github.jarlh19.tienda.pedidos.client.ResultadoReserva;
import io.github.jarlh19.tienda.pedidos.config.SagaProperties;
import io.github.jarlh19.tienda.pedidos.exception.SagaInterrumpidaException;
import io.github.jarlh19.tienda.pedidos.model.EstadoPedido;
import io.github.jarlh19.tienda.pedidos.model.LineaPedido;
import io.github.jarlh19.tienda.pedidos.model.Pedido;
import io.github.jarlh19.tienda.pedidos.repository.PedidoRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Cada camino de la saga, con inventario y pagos simulados. */
@SpringBootTest
class SagaPedidoTest {

    static final List<LineaPedido> LINEAS = List.of(new LineaPedido(2L, 1), new LineaPedido(1L, 2));

    @MockitoBean
    InventarioClient inventario;

    @MockitoBean
    PagosClient pagos;

    @Autowired
    PedidoService pedidoService;

    @Autowired
    SagaPedido saga;

    @Autowired
    PedidoRepository pedidos;

    @BeforeEach
    void sinPedidosPrevios() {
        pedidos.deleteAll();
    }

    @Test
    void confirmaElPedidoSiHayStockYElPagoSeAprueba() {
        when(inventario.reservar(anyLong(), any())).thenReturn(new ResultadoReserva.Reservada(new BigDecimal("280.80")));
        when(pagos.cobrar(anyLong(), any())).thenReturn(new ResultadoPago.Aprobado());

        Pedido pedido = pedidoService.crear("Ana", LINEAS);

        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.CONFIRMADO);
        assertThat(pedido.getTotal()).isEqualByComparingTo("280.80");
        verify(pagos).cobrar(pedido.getId(), new BigDecimal("280.80"));
        verify(inventario, never()).liberar(anyLong());
    }

    @Test
    void sinStockSeCancelaSinCobrarNiCompensar() {
        when(inventario.reservar(anyLong(), any()))
                .thenReturn(new ResultadoReserva.Rechazada("STOCK_INSUFICIENTE: no alcanza"));

        Pedido pedido = pedidoService.crear("Ana", LINEAS);

        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.CANCELADO);
        assertThat(pedido.getMotivo()).startsWith("STOCK_INSUFICIENTE");
        verify(pagos, never()).cobrar(anyLong(), any());
        verify(inventario, never()).liberar(anyLong());
    }

    @Test
    void siElPagoSeRechazaSeLiberaElStock() {
        when(inventario.reservar(anyLong(), any())).thenReturn(new ResultadoReserva.Reservada(new BigDecimal("2899.00")));
        when(pagos.cobrar(anyLong(), any())).thenReturn(new ResultadoPago.Rechazado("PAGO_RECHAZADO: fondos insuficientes"));
        when(inventario.liberar(anyLong())).thenReturn(true);

        Pedido pedido = pedidoService.crear("Ana", LINEAS);

        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.CANCELADO);
        assertThat(pedido.getMotivo()).startsWith("PAGO_RECHAZADO");
        verify(inventario).liberar(pedido.getId());
        verify(pagos, never()).reembolsar(anyLong());
    }

    @Test
    void conElCircuitoDeInventarioAbiertoSeCancelaSinCompensar() {
        when(inventario.reservar(anyLong(), any()))
                .thenReturn(new ResultadoReserva.NoDisponible("circuito abierto", false));

        Pedido pedido = pedidoService.crear("Ana", LINEAS);

        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.CANCELADO);
        assertThat(pedido.getMotivo()).startsWith("INVENTARIO_NO_DISPONIBLE");
        verify(inventario, never()).liberar(anyLong());
    }

    @Test
    void siInventarioNoRespondeSeLiberaPorSiLaReservaSeHizo() {
        when(inventario.reservar(anyLong(), any()))
                .thenReturn(new ResultadoReserva.NoDisponible("timeout", true));
        when(inventario.liberar(anyLong())).thenReturn(true);

        Pedido pedido = pedidoService.crear("Ana", LINEAS);

        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.CANCELADO);
        verify(inventario).liberar(pedido.getId());
        verify(pagos, never()).cobrar(anyLong(), any());
    }

    @Test
    void siPagosNoRespondeSeReembolsaYLuegoSeLiberaElStock() {
        when(inventario.reservar(anyLong(), any())).thenReturn(new ResultadoReserva.Reservada(new BigDecimal("45.90")));
        when(pagos.cobrar(anyLong(), any())).thenReturn(new ResultadoPago.NoDisponible("timeout", true));
        when(pagos.reembolsar(anyLong())).thenReturn(true);
        when(inventario.liberar(anyLong())).thenReturn(true);

        Pedido pedido = pedidoService.crear("Ana", LINEAS);

        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.CANCELADO);
        InOrder orden = inOrder(pagos, inventario);
        orden.verify(pagos).reembolsar(pedido.getId());
        orden.verify(inventario).liberar(pedido.getId());
    }

    @Test
    void siUnaCompensacionFallaElPedidoQuedaCompensandoHastaQueElReintentoLaLogra() {
        when(inventario.reservar(anyLong(), any())).thenReturn(new ResultadoReserva.Reservada(new BigDecimal("2899.00")));
        when(pagos.cobrar(anyLong(), any())).thenReturn(new ResultadoPago.Rechazado("PAGO_RECHAZADO: fondos insuficientes"));
        when(inventario.liberar(anyLong())).thenReturn(false);

        Pedido pedido = pedidoService.crear("Ana", LINEAS);

        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.COMPENSANDO);
        assertThat(pedido.compensacionesPendientes()).containsExactly("LIBERAR_STOCK");
        assertThat(pedido.getIntentosCompensacion()).isEqualTo(1);

        when(inventario.liberar(anyLong())).thenReturn(true);
        reintentosSinMargen().reintentarCompensaciones();

        Pedido despues = pedidos.findById(pedido.getId()).orElseThrow();
        assertThat(despues.getEstado()).isEqualTo(EstadoPedido.CANCELADO);
        assertThat(despues.compensacionesPendientes()).isEmpty();
    }

    @Test
    void unaSagaQueQuedoAMediasSeCompensa() {
        Pedido pedido = Pedido.nuevo("Ana", LINEAS);
        pedido.stockReservado(new BigDecimal("100.00"));
        pedido = pedidos.save(pedido);
        when(pagos.reembolsar(anyLong())).thenReturn(true);
        when(inventario.liberar(anyLong())).thenReturn(true);

        reintentosSinMargen().compensarSagasInterrumpidas();

        Pedido despues = pedidos.findById(pedido.getId()).orElseThrow();
        assertThat(despues.getEstado()).isEqualTo(EstadoPedido.CANCELADO);
        assertThat(despues.getMotivo()).startsWith("SAGA_INTERRUMPIDA");
        verify(pagos).reembolsar(pedido.getId());
        verify(inventario).liberar(pedido.getId());
    }

    @Test
    void unaCompensacionRecienEmpezadaNoSeReintentaParaNoPisarALaPeticionEnCurso() {
        when(inventario.reservar(anyLong(), any())).thenReturn(new ResultadoReserva.Reservada(new BigDecimal("2899.00")));
        when(pagos.cobrar(anyLong(), any())).thenReturn(new ResultadoPago.Rechazado("PAGO_RECHAZADO: fondos insuficientes"));
        when(inventario.liberar(anyLong())).thenReturn(false);
        Pedido pedido = pedidoService.crear("Ana", LINEAS);

        new ReintentosSaga(pedidos, saga, new SagaProperties(Duration.ofSeconds(10), Duration.ofSeconds(60), 30))
                .reintentarCompensaciones();

        verify(inventario, times(1)).liberar(pedido.getId());
    }

    @Test
    void unErrorInesperadoEnLaSagaDevuelveElIdDelPedidoYaGuardado() {
        when(inventario.reservar(anyLong(), any())).thenThrow(new IllegalStateException("bug"));

        assertThatThrownBy(() -> pedidoService.crear("Ana", LINEAS))
                .isInstanceOfSatisfying(SagaInterrumpidaException.class, e -> {
                    Pedido guardado = pedidos.findById(e.getPedidoId()).orElseThrow();
                    assertThat(guardado.getEstado()).isEqualTo(EstadoPedido.PENDIENTE);
                });
    }

    /** Plazos negativos: cualquier pedido cuenta como colgado y cualquier compensación como vencida. */
    private ReintentosSaga reintentosSinMargen() {
        Duration negativo = Duration.ofSeconds(-1);
        return new ReintentosSaga(pedidos, saga, new SagaProperties(negativo, negativo, 30));
    }
}
