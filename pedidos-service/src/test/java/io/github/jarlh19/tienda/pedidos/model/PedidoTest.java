package io.github.jarlh19.tienda.pedidos.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PedidoTest {

    Pedido pedido = Pedido.nuevo("Ana", List.of(new LineaPedido(1L, 1)));

    @Test
    void unMotivoLargoSeRecortaParaQueQuepaEnLaColumna() {
        pedido.cancelar("HTTP_404: " + "<html>".repeat(200));

        assertThat(pedido.getMotivo()).hasSize(500).endsWith("…");
    }

    @Test
    void noSePuedeConfirmarSinHaberReservadoStock() {
        assertThatThrownBy(pedido::confirmar).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void elPedidoSeCancelaRecienCuandoTerminanTodasLasCompensaciones() {
        pedido.stockReservado(new BigDecimal("10.00"));
        pedido.compensar("PAGOS_NO_DISPONIBLE: timeout", true, true);

        pedido.pagoReembolsado();
        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.COMPENSANDO);

        pedido.stockLiberado();
        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.CANCELADO);
    }
}
