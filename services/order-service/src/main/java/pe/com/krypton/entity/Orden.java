package pe.com.krypton.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pe.com.krypton.entity.enums.EstadoOrden;
import pe.com.krypton.entity.enums.MetodoPago;
import pe.com.krypton.entity.enums.TipoDocumento;

/**
 * Pedido. total = subtotal + shipping_cost. payment_method NULL hasta que se paga.
 * ADAPTACION microservicios: el dueño se guarda como user_email suelto (identidad lógica
 * -> users-service), SIN @ManyToOne a Usuario. Los items se cargan por repo (SIN @OneToMany).
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class Orden {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_email", nullable = false)
    private String userEmail;

    @Column(name = "order_date", nullable = false)
    private Instant orderDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoOrden status;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 10)
    private TipoDocumento documentType;

    @Column(name = "customer_name", nullable = false, length = 150)
    private String customerName;

    @Column(name = "customer_doc", nullable = false, length = 11)
    private String customerDoc;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "shipping_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal shippingCost;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal igv;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private MetodoPago paymentMethod;
}
