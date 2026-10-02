package io.github.jarlh19.tienda.inventario.controller;

import io.github.jarlh19.tienda.inventario.dto.ProductoResponse;
import io.github.jarlh19.tienda.inventario.dto.ReservaRequest;
import io.github.jarlh19.tienda.inventario.dto.ReservaResponse;
import io.github.jarlh19.tienda.inventario.service.ProductoService;
import io.github.jarlh19.tienda.inventario.service.ReservaService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/inventario")
@RequiredArgsConstructor
public class InventarioController {

    private final ProductoService productoService;
    private final ReservaService reservaService;

    @GetMapping("/productos")
    public List<ProductoResponse> listarProductos() {
        return productoService.listar().stream().map(ProductoResponse::de).toList();
    }

    @GetMapping("/productos/{id}")
    public ProductoResponse buscarProducto(@PathVariable Long id) {
        return ProductoResponse.de(productoService.buscar(id));
    }

    @Operation(summary = "Reserva stock para un pedido (paso 1 de la saga, idempotente)")
    @PostMapping("/reservas")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservaResponse reservar(@Valid @RequestBody ReservaRequest solicitud) {
        return ReservaResponse.de(reservaService.reservar(solicitud.pedidoId(), solicitud.itemsSolicitados()));
    }

    @Operation(summary = "Devuelve el stock reservado (compensación del paso 1, idempotente)")
    @PostMapping("/reservas/{pedidoId}/liberar")
    public ReservaResponse liberar(@PathVariable Long pedidoId) {
        return ReservaResponse.de(reservaService.liberar(pedidoId));
    }

    @GetMapping("/reservas/{pedidoId}")
    public ResponseEntity<ReservaResponse> buscarReserva(@PathVariable Long pedidoId) {
        return ResponseEntity.of(reservaService.buscar(pedidoId).map(ReservaResponse::de));
    }
}
