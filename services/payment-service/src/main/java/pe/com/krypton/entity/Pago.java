package pe.com.krypton.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pe.com.krypton.entity.enums.PaymentStatus;

/**
 * Cobro de un pedido. Un registro por intento de pago del checkout.
 * ADAPTACION microservicios: el pedido se guarda como order_id suelto (identidad lógica
 * -> order-service), SIN @ManyToOne a Orden (esa tabla vive en otra base).
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 20)
    private String method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
