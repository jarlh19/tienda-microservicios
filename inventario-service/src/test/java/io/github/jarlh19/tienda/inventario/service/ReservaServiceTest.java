package io.github.jarlh19.tienda.inventario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.jarlh19.tienda.inventario.exception.ProductoNoEncontradoException;
import io.github.jarlh19.tienda.inventario.exception.ReservaYaLiberadaException;
import io.github.jarlh19.tienda.inventario.exception.StockInsuficienteException;
import io.github.jarlh19.tienda.inventario.model.EstadoReserva;
import io.github.jarlh19.tienda.inventario.model.Producto;
import io.github.jarlh19.tienda.inventario.model.Reserva;
import io.github.jarlh19.tienda.inventario.repository.ProductoRepository;
import io.github.jarlh19.tienda.inventario.repository.ReservaRepository;
import io.github.jarlh19.tienda.inventario.service.ReservaService.ItemSolicitado;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sin la transacción envolvente de @DataJpaTest: cada llamada al servicio confirma o revierte la
 * suya, igual que en producción. Así se puede comprobar que un fallo no deja stock apartado.
 */
@DataJpaTest
@Import(ReservaService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReservaServiceTest {

    @Autowired
    ReservaService servicio;

    @Autowired
    ProductoRepository productos;

    @Autowired
    ReservaRepository reservas;

    Producto teclado;
    Producto mouse;

    @BeforeEach
    void catalogoLimpio() {
        reservas.deleteAll();
        productos.deleteAll();
        teclado = productos.save(new Producto("Teclado", new BigDecimal("100.00"), 5));
        mouse = productos.save(new Producto("Mouse", new BigDecimal("20.50"), 10));
    }

    @Test
    void reservarDescuentaElStockYCalculaElTotal() {
        Reserva reserva = servicio.reservar(1L, List.of(
                new ItemSolicitado(teclado.getId(), 2),
                new ItemSolicitado(mouse.getId(), 3)));

        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.RESERVADA);
        assertThat(reserva.total()).isEqualByComparingTo("261.50");
        assertThat(stockDe(teclado)).isEqualTo(3);
        assertThat(stockDe(mouse)).isEqualTo(7);
    }

    @Test
    void reservarDosVecesElMismoPedidoNoDescuentaDosVeces() {
        List<ItemSolicitado> items = List.of(new ItemSolicitado(teclado.getId(), 2));

        servicio.reservar(1L, items);
        servicio.reservar(1L, items);

        assertThat(stockDe(teclado)).isEqualTo(3);
        assertThat(reservas.count()).isEqualTo(1);
    }

    @Test
    void siFaltaStockDeUnProductoNoSeApartaNingunoDelPedido() {
        List<ItemSolicitado> items = List.of(
                new ItemSolicitado(mouse.getId(), 1),
                new ItemSolicitado(teclado.getId(), 6));

        assertThatThrownBy(() -> servicio.reservar(1L, items))
                .isInstanceOf(StockInsuficienteException.class)
                .hasMessageContaining("Teclado");

        assertThat(stockDe(mouse)).isEqualTo(10);
        assertThat(stockDe(teclado)).isEqualTo(5);
        assertThat(reservas.count()).isZero();
    }

    @Test
    void reservarUnProductoQueNoExisteFalla() {
        assertThatThrownBy(() -> servicio.reservar(1L, List.of(new ItemSolicitado(999L, 1))))
                .isInstanceOf(ProductoNoEncontradoException.class);
    }

    @Test
    void liberarDevuelveElStockYEsIdempotente() {
        servicio.reservar(1L, List.of(new ItemSolicitado(teclado.getId(), 4)));

        servicio.liberar(1L);
        Reserva reserva = servicio.liberar(1L);

        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.LIBERADA);
        assertThat(stockDe(teclado)).isEqualTo(5);
    }

    @Test
    void unaReservaQueLlegaDespuesDeSuCompensacionSeRechaza() {
        servicio.liberar(7L);

        assertThatThrownBy(() -> servicio.reservar(7L, List.of(new ItemSolicitado(teclado.getId(), 1))))
                .isInstanceOf(ReservaYaLiberadaException.class);
        assertThat(stockDe(teclado)).isEqualTo(5);
    }

    private int stockDe(Producto producto) {
        return productos.findById(producto.getId()).orElseThrow().getStock();
    }
}
