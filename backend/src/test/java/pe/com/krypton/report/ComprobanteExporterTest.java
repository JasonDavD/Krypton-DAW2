package pe.com.krypton.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.com.krypton.entity.Order;
import pe.com.krypton.entity.OrderItem;
import pe.com.krypton.entity.Product;
import pe.com.krypton.entity.enums.DocumentType;
import pe.com.krypton.entity.enums.OrderStatus;
import pe.com.krypton.entity.enums.PaymentMethod;

/**
 * Pure-Java unit tests for ComprobanteExporter. No Spring context.
 * Asserts %PDF magic bytes (0x25 0x50 0x44 0x46) + non-empty output.
 */
class ComprobanteExporterTest {

    private static final byte[] PDF_MAGIC = new byte[]{ 0x25, 0x50, 0x44, 0x46 }; // %PDF

    ComprobanteExporter exporter;

    @BeforeEach
    void setUp() {
        exporter = new ComprobanteExporter();
    }

    @Test
    void boleta_with_items_produces_pdf_magic() {
        Order order = order(DocumentType.BOLETA, "Juan Cliente", "12345678", PaymentMethod.YAPE);
        byte[] bytes = exporter.export(order, List.of(item("Laptop", 1, new BigDecimal("2999.90"))));
        assertPdf(bytes);
    }

    @Test
    void factura_with_items_produces_pdf_magic() {
        Order order = order(DocumentType.FACTURA, "ACME S.A.C.", "20123456789", PaymentMethod.CREDIT_CARD);
        byte[] bytes = exporter.export(order, List.of(item("Mouse", 2, new BigDecimal("99.90"))));
        assertPdf(bytes);
    }

    @Test
    void no_payment_method_and_no_items_still_valid_pdf() {
        Order order = order(DocumentType.BOLETA, "Juan", "12345678", null);
        byte[] bytes = exporter.export(order, List.of());
        assertPdf(bytes);
    }

    private void assertPdf(byte[] bytes) {
        assertThat(bytes).isNotNull();
        assertThat(bytes.length).isGreaterThan(4);
        assertThat(bytes[0]).isEqualTo(PDF_MAGIC[0]);
        assertThat(bytes[1]).isEqualTo(PDF_MAGIC[1]);
        assertThat(bytes[2]).isEqualTo(PDF_MAGIC[2]);
        assertThat(bytes[3]).isEqualTo(PDF_MAGIC[3]);
    }

    private Order order(DocumentType type, String name, String doc, PaymentMethod method) {
        Order o = new Order();
        o.setId(1L);
        o.setOrderDate(Instant.now());
        o.setStatus(OrderStatus.CONFIRMADA);
        o.setDocumentType(type);
        o.setCustomerName(name);
        o.setCustomerDoc(doc);
        o.setSubtotal(new BigDecimal("100.00"));
        o.setShippingCost(new BigDecimal("20.00"));
        o.setIgv(new BigDecimal("18.31"));
        o.setTotal(new BigDecimal("120.00"));
        o.setPaymentMethod(method);
        return o;
    }

    private OrderItem item(String name, int qty, BigDecimal unitPrice) {
        Product p = new Product();
        p.setName(name);
        OrderItem oi = new OrderItem();
        oi.setProduct(p);
        oi.setQuantity(qty);
        oi.setUnitPrice(unitPrice);
        return oi;
    }
}
