package io.github.jarlh19.tienda.pagos;

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
import org.springframework.test.web.servlet.ResultActions;

/** Cada prueba usa su propio número de pedido, así no dependen del orden en que corren. */
@SpringBootTest
@AutoConfigureMockMvc
class PagoApiTest {

    @Autowired
    MockMvc mvc;

    @Test
    void apruebaUnMontoDentroDelLimite() throws Exception {
        cobrar(1, "999.99")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("APROBADO"));
    }

    @Test
    void rechazaUnMontoSobreElLimite() throws Exception {
        cobrar(2, "1000.01")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("RECHAZADO"))
                .andExpect(jsonPath("$.motivo").value("Fondos insuficientes: el monto supera el límite de 1000.00"));
    }

    @Test
    void unReintentoDelMismoPedidoDevuelveElMismoPagoSinCobrarOtraVez() throws Exception {
        cobrar(3, "50.00").andExpect(jsonPath("$.estado").value("APROBADO"));

        cobrar(3, "5000.00")
                .andExpect(jsonPath("$.estado").value("APROBADO"))
                .andExpect(jsonPath("$.monto").value(50.00));
    }

    @Test
    void reembolsarDevuelveElDineroYEsIdempotente() throws Exception {
        cobrar(4, "80.00");

        mvc.perform(post("/api/pagos/4/reembolso")).andExpect(jsonPath("$.estado").value("REEMBOLSADO"));
        mvc.perform(post("/api/pagos/4/reembolso")).andExpect(jsonPath("$.estado").value("REEMBOLSADO"));
    }

    @Test
    void unCobroQueLlegaDespuesDeSuCompensacionNoSeAprueba() throws Exception {
        mvc.perform(post("/api/pagos/5/reembolso")).andExpect(jsonPath("$.estado").value("ANULADO"));

        cobrar(5, "10.00").andExpect(jsonPath("$.estado").value("ANULADO"));
    }

    @Test
    void consultarUnPagoQueNoExisteDevuelve404() throws Exception {
        mvc.perform(get("/api/pagos/999")).andExpect(status().isNotFound());
    }

    @Test
    void unMontoNegativoEsUnaSolicitudInvalida() throws Exception {
        cobrar(6, "-1").andExpect(status().isBadRequest());
    }

    private ResultActions cobrar(long pedidoId, String monto) throws Exception {
        return mvc.perform(post("/api/pagos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pedidoId\": %d, \"monto\": %s}".formatted(pedidoId, monto)));
    }
}
