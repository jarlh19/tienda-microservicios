package io.github.jarlh19.tienda.pedidos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LineaPedido {

    @Column(nullable = false)
    private Long productoId;

    @Column(nullable = false)
    private int cantidad;
}
