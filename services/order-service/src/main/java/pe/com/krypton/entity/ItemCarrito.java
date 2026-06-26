package pe.com.krypton.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Línea del carrito. NO guarda precio (se muestra el precio VIVO del producto, vía Feign).
 * product_id es FK lógica -> catalog-service (no hay @ManyToOne a Producto: vive en otro servicio).
 */
@Entity
@Table(name = "cart_item")
@Getter
@Setter
@NoArgsConstructor
public class ItemCarrito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id")
    private Carrito cart;

    @Column(name = "product_id")
    private Long productId;

    @Column(nullable = false)
    private int quantity;
}
