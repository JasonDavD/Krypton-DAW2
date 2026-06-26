package pe.com.krypton.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pe.com.krypton.entity.enums.TipoMovimiento;

@Entity
@Table(name = "stock_movement")
@Getter
@Setter
@NoArgsConstructor
public class MovimientoStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Producto product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoMovimiento type;

    @Column(nullable = false)
    private int quantity;

    private String reason;

    private String reference;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // Admin que registró el movimiento (nullable)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Usuario createdBy;
}
