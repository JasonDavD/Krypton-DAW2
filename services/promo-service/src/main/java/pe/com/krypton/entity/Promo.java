package pe.com.krypton.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pe.com.krypton.entity.enums.DescuentoTipo;

/**
 * Codigo de descuento (cupon). code es la clave natural (unica); el descuento se interpreta
 * segun type: PORCENTAJE -> value es el % a descontar; MONTO -> value es el importe fijo.
 */
@Entity
@Table(name = "promos")
@Getter
@Setter
@NoArgsConstructor
public class Promo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DescuentoTipo type;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal value;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
