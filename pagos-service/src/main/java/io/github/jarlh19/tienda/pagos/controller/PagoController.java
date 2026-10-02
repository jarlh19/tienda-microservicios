package io.github.jarlh19.tienda.pagos.controller;

import io.github.jarlh19.tienda.pagos.dto.CobroRequest;
import io.github.jarlh19.tienda.pagos.dto.PagoResponse;
import io.github.jarlh19.tienda.pagos.service.PagoService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pagos")
@RequiredArgsConstructor
public class PagoController {

    private final PagoService servicio;

    /** Un cobro rechazado también responde 201: el pago existe, con estado RECHAZADO y su motivo. */
    @Operation(summary = "Cobra un pedido (paso 2 de la saga, idempotente)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PagoResponse cobrar(@Valid @RequestBody CobroRequest solicitud) {
        return PagoResponse.de(servicio.cobrar(solicitud.pedidoId(), solicitud.monto()));
    }

    @Operation(summary = "Devuelve el dinero de un pedido (compensación del paso 2, idempotente)")
    @PostMapping("/{pedidoId}/reembolso")
    public PagoResponse reembolsar(@PathVariable Long pedidoId) {
        return PagoResponse.de(servicio.reembolsar(pedidoId));
    }

    @GetMapping("/{pedidoId}")
    public ResponseEntity<PagoResponse> buscar(@PathVariable Long pedidoId) {
        return ResponseEntity.of(servicio.buscar(pedidoId).map(PagoResponse::de));
    }
}
