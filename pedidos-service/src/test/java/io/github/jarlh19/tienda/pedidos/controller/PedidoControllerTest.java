package io.github.jarlh19.tienda.pedidos.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.jarlh19.tienda.pedidos.client.InventarioClient;
import io.github.jarlh19.tienda.pedidos.client.PagosClient;
import io.github.jarlh19.tienda.pedidos.client.ResultadoPago;
import io.github.jarlh19.tienda.pedidos.client.ResultadoReserva;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PedidoControllerTest {

    @MockitoBean
    InventarioClient inventario;

    @MockitoBean
    PagosClient pagos;

    @Autowired
    MockMvc mvc;

    @Test
    void creaElPedidoYDevuelveElEstadoFinalDeLaSaga() throws Exception {
        when(inventario.reservar(anyLong(), any())).thenReturn(new ResultadoReserva.Reservada(new BigDecimal("280.80")));
        when(pagos.cobrar(anyLong(), any())).thenReturn(new ResultadoPago.Aprobado());

        mvc.perform(post("/api/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cliente": "Ana", "lineas": [{"productoId": 2, "cantidad": 1}, {"productoId": 1, "cantidad": 2}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("CONFIRMADO"))
                .andExpect(jsonPath("$.total").value(280.80))
                .andExpect(jsonPath("$.lineas.length()").value(2));
    }

    @Test
    void unPedidoSinLineasEsUnaSolicitudInvalida() throws Exception {
        mvc.perform(post("/api/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cliente": "Ana", "lineas": []}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void siLaSagaSeRompeResponde500ConElIdDelPedidoParaConsultarloEnVezDeReintentar() throws Exception {
        when(inventario.reservar(anyLong(), any())).thenThrow(new IllegalStateException("bug"));

        mvc.perform(post("/api/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cliente": "Ana", "lineas": [{"productoId": 1, "cantidad": 1}]}
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("SAGA_INTERRUMPIDA"))
                .andExpect(jsonPath("$.pedidoId").isNumber());
    }

    @Test
    void consultarUnPedidoQueNoExisteDevuelve404ConCodigo() throws Exception {
        mvc.perform(get("/api/pedidos/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PEDIDO_NO_EXISTE"));
    }
}
