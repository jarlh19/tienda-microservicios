package io.github.jarlh19.tienda.inventario.config;

import io.github.jarlh19.tienda.inventario.model.Producto;
import io.github.jarlh19.tienda.inventario.repository.ProductoRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** Catálogo de ejemplo para la demo. Solo se carga si la tabla está vacía. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatosIniciales implements CommandLineRunner {

    private final ProductoRepository productos;

    @Override
    public void run(String... args) {
        if (productos.count() > 0) {
            return;
        }
        productos.saveAll(List.of(
                new Producto("Mouse inalámbrico", new BigDecimal("45.90"), 50),
                new Producto("Teclado mecánico", new BigDecimal("189.00"), 20),
                new Producto("Monitor 24 pulgadas", new BigDecimal("649.00"), 8),
                new Producto("Laptop 14 pulgadas", new BigDecimal("2899.00"), 3)));
        log.info("Catálogo de ejemplo cargado");
    }
}
