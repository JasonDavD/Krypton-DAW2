package pe.com.krypton.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.com.krypton.entity.Orden;
import pe.com.krypton.entity.ItemOrden;
import pe.com.krypton.entity.Producto;
import pe.com.krypton.entity.enums.TipoDocumento;
import pe.com.krypton.entity.enums.EstadoOrden;
import pe.com.krypton.entity.enums.MetodoPago;

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
        Orden order = order(TipoDocumento.BOLETA, "Juan Cliente", "12345678", MetodoPago.YAPE);
        byte[] bytes = exporter.export(order, List.of(item("Laptop", 1, new BigDecimal("2999.90"))));
        assertPdf(bytes);
    }

    @Test
    void factura_with_items_produces_pdf_magic() {
        Orden order = order(TipoDocumento.FACTURA, "ACME S.A.C.", "20123456789", MetodoPago.CREDIT_CARD);
        byte[] bytes = exporter.export(order, List.of(item("Mouse", 2, new BigDecimal("99.90"))));
        assertPdf(bytes);
    }

    @Test
    void no_payment_method_and_no_items_still_valid_pdf() {
        Orden order = order(TipoDocumento.BOLETA, "Juan", "12345678", null);
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

    private Orden order(TipoDocumento type, String name, String doc, MetodoPago method) {
        Orden o = new Orden();
        o.setId(1L);
        o.setOrderDate(Instant.now());
        o.setStatus(EstadoOrden.CONFIRMADA);
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

    private ItemOrden item(String name, int qty, BigDecimal unitPrice) {
        Producto p = new Producto();
        p.setName(name);
        ItemOrden oi = new ItemOrden();
        oi.setProduct(p);
        oi.setQuantity(qty);
        oi.setUnitPrice(unitPrice);
        return oi;
    }
}
