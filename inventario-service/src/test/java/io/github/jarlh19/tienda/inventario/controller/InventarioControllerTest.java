package io.github.jarlh19.tienda.inventario.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Prueba la API con el catálogo de ejemplo que carga DatosIniciales. */
@SpringBootTest
@AutoConfigureMockMvc
class InventarioControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void listaElCatalogo() throws Exception {
        mvc.perform(get("/api/inventario/productos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].nombre").value("Mouse inalámbrico"));
    }

    @Test
    void reservaYDevuelveElTotal() throws Exception {
        mvc.perform(post("/api/inventario/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pedidoId": 100, "items": [{"productoId": 1, "cantidad": 2}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("RESERVADA"))
                .andExpect(jsonPath("$.total").value(91.80));
    }

    @Test
    void sinStockRespondeConflictoConCodigoDeNegocio() throws Exception {
        mvc.perform(post("/api/inventario/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pedidoId": 101, "items": [{"productoId": 4, "cantidad": 99}]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("STOCK_INSUFICIENTE"));
    }

    @Test
    void unaSolicitudInvalidaRespondeBadRequest() throws Exception {
        mvc.perform(post("/api/inventario/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pedidoId": 102, "items": []}
                                """))
                .andExpect(status().isBadRequest());
    }
}
