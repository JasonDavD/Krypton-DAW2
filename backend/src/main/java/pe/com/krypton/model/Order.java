package pe.com.krypton.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pe.com.krypton.model.enums.DocumentType;
import pe.com.krypton.model.enums.OrderStatus;
import pe.com.krypton.model.enums.PaymentMethod;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "order_date", nullable = false)
    private Instant orderDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    // ── Comprobante (boleta/factura) ──
    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 10)
    private DocumentType documentType;

    /** Nombre (boleta) o razón social (factura) del receptor. */
    @Column(name = "customer_name", nullable = false, length = 150)
    private String customerName;

    /** DNI (8 díg, boleta) o RUC (11 díg, factura). */
    @Column(name = "customer_doc", nullable = false, length = 11)
    private String customerDoc;

    // ── Desglose de montos (el precio del catálogo ya incluye IGV) ──
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "shipping_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal shippingCost;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal igv;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    /** Método de pago elegido al pagar. Null mientras el pedido sigue PENDIENTE. */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private PaymentMethod paymentMethod;
}
