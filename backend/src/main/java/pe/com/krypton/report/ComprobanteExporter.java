package pe.com.krypton.report;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import pe.com.krypton.entity.Order;
import pe.com.krypton.entity.OrderItem;
import pe.com.krypton.entity.enums.DocumentType;
import pe.com.krypton.entity.enums.PaymentMethod;

/**
 * Genera el PDF del comprobante (boleta/factura) de un pedido, usando OpenPDF.
 * A diferencia de {@link PdfExporter} (reportes agregados de admin), esto emite el
 * documento individual del cliente: receptor, líneas, desglose y método de pago.
 * Lee la entidad Order directamente (server-side, no es contrato de API).
 */
@Component
public class ComprobanteExporter {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(LIMA);
    private static final Color HEADER_BG = new Color(180, 180, 180);

    /** Renderiza el comprobante a partir del pedido y sus líneas (ya cargadas). */
    public byte[] export(Order order, List<OrderItem> items) {
        boolean factura = order.getDocumentType() == DocumentType.FACTURA;
        return render(doc -> {
            doc.add(title("KRYPTON E-COMMERCE"));
            doc.add(subtitle(factura ? "FACTURA ELECTRÓNICA" : "BOLETA DE VENTA ELECTRÓNICA"));

            doc.add(line("Comprobante N°: " + String.format("%08d", order.getId())));
            doc.add(line("Fecha: " + DT_FMT.format(order.getOrderDate())));
            doc.add(line((factura ? "Razón social: " : "Cliente: ") + order.getCustomerName()));
            doc.add(line((factura ? "RUC: " : "DNI: ") + order.getCustomerDoc()));
            doc.add(line("Método de pago: " + methodLabel(order.getPaymentMethod())));
            doc.add(spacer());

            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            try {
                table.setWidths(new float[]{ 5f, 1.2f, 2f, 2f });
            } catch (Exception ignored) {
                // setWidths sólo falla si el nro de anchos != columnas; aquí coincide.
            }
            addHeaderCell(table, "Producto");
            addHeaderCell(table, "Cant.");
            addHeaderCell(table, "P. Unit (S/)");
            addHeaderCell(table, "Subtotal (S/)");
            for (OrderItem it : items) {
                BigDecimal sub = it.getUnitPrice().multiply(BigDecimal.valueOf(it.getQuantity()));
                table.addCell(it.getProduct().getName());
                table.addCell(String.valueOf(it.getQuantity()));
                table.addCell(it.getUnitPrice().toPlainString());
                table.addCell(sub.toPlainString());
            }
            doc.add(table);
            doc.add(spacer());

            doc.add(totalLine("Subtotal (S/): " + order.getSubtotal().toPlainString(), false));
            doc.add(totalLine("Envío (S/): " + order.getShippingCost().toPlainString(), false));
            doc.add(totalLine("IGV 18% incluido (S/): " + order.getIgv().toPlainString(), false));
            doc.add(totalLine("TOTAL (S/): " + order.getTotal().toPlainString(), true));
        });
    }

    /** Etiqueta legible del método de pago; null = pedido sin pago registrado. */
    private String methodLabel(PaymentMethod method) {
        if (method == null) {
            return "—";
        }
        return switch (method) {
            case CREDIT_CARD -> "Tarjeta de crédito";
            case DEBIT_CARD -> "Tarjeta de débito";
            case YAPE -> "Yape";
        };
    }

    // ─── private helpers (mismo estilo que PdfExporter) ───────────────────────────

    private byte[] render(Consumer<Document> body) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4);
        try {
            PdfWriter.getInstance(doc, baos);
            doc.open();
            body.accept(doc);
        } finally {
            if (doc.isOpen()) {
                doc.close();
            }
        }
        return baos.toByteArray();
    }

    private Paragraph title(String text) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
        Paragraph p = new Paragraph(text, f);
        p.setAlignment(Element.ALIGN_CENTER);
        p.setSpacingAfter(2);
        return p;
    }

    private Paragraph subtitle(String text) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Font.BOLD, new Color(90, 90, 90));
        Paragraph p = new Paragraph(text, f);
        p.setAlignment(Element.ALIGN_CENTER);
        p.setSpacingAfter(12);
        return p;
    }

    private Paragraph line(String text) {
        Paragraph p = new Paragraph(text, FontFactory.getFont(FontFactory.HELVETICA, 10));
        p.setSpacingAfter(3);
        return p;
    }

    private Paragraph totalLine(String text, boolean bold) {
        Font f = FontFactory.getFont(bold ? FontFactory.HELVETICA_BOLD : FontFactory.HELVETICA, bold ? 12 : 10);
        Paragraph p = new Paragraph(text, f);
        p.setAlignment(Element.ALIGN_RIGHT);
        p.setSpacingAfter(3);
        return p;
    }

    private Paragraph spacer() {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(6);
        return p;
    }

    private void addHeaderCell(PdfPTable table, String text) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.BOLD, Color.WHITE);
        PdfPCell cell = new PdfPCell(new Phrase(text, f));
        cell.setBackgroundColor(HEADER_BG);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(5);
        table.addCell(cell);
    }
}
