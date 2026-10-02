package io.github.jarlh19.tienda.pedidos.controller;

import io.github.jarlh19.tienda.pedidos.dto.CrearPedidoRequest;
import io.github.jarlh19.tienda.pedidos.dto.PedidoResponse;
import io.github.jarlh19.tienda.pedidos.service.PedidoService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pedidos")
@RequiredArgsConstructor
public class PedidoController {

    private final PedidoService servicio;

    /**
     * Responde 201 aunque el pedido termine cancelado: el pedido existe y su estado y motivo dicen
     * qué pasó en la saga.
     */
    @Operation(summary = "Crea un pedido y ejecuta la saga (reservar stock y cobrar)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PedidoResponse crear(@Valid @RequestBody CrearPedidoRequest solicitud) {
        return PedidoResponse.de(servicio.crear(solicitud.cliente(), solicitud.lineasDelPedido()));
    }

    @GetMapping("/{id}")
    public PedidoResponse buscar(@PathVariable Long id) {
        return PedidoResponse.de(servicio.buscar(id));
    }

    @GetMapping
    public List<PedidoResponse> listar() {
        return servicio.listar().stream().map(PedidoResponse::de).toList();
    }
}
