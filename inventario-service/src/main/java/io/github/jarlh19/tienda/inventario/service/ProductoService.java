package io.github.jarlh19.tienda.inventario.service;

import io.github.jarlh19.tienda.inventario.exception.ProductoNoEncontradoException;
import io.github.jarlh19.tienda.inventario.model.Producto;
import io.github.jarlh19.tienda.inventario.repository.ProductoRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductoService {

    private final ProductoRepository productos;

    public List<Producto> listar() {
        return productos.findAll(Sort.by("id"));
    }

    public Producto buscar(Long id) {
        return productos.findById(id).orElseThrow(() -> new ProductoNoEncontradoException(id));
    }
}
