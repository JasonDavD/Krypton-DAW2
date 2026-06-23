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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import pe.com.krypton.dto.response.OrderResponse;
import pe.com.krypton.dto.response.report.KardexMovimientoRow;
import pe.com.krypton.dto.response.report.KardexReport;
import pe.com.krypton.dto.response.report.OrdenesListadoReport;
import pe.com.krypton.dto.response.report.TopProductoRow;
import pe.com.krypton.dto.response.report.TopProductosReport;
import pe.com.krypton.dto.response.report.VentasPeriodoRow;
import pe.com.krypton.dto.response.report.VentasPorPeriodoReport;

/** Genera archivos PDF a partir de los DTOs de reporte usando OpenPDF. Un método por reporte. */
@Component
public class PdfExporter {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(LIMA);

    private static final Color HEADER_BG = new Color(180, 180, 180);

    // ─── R1: Ventas por período ──────────────────────────────────────────────────

    public byte[] exportVentas(VentasPorPeriodoReport report) {
        return render(doc -> {
            doc.add(title("Reporte: Ventas por Período"));
            doc.add(summaryLine("Total Órdenes: " + report.totalOrdenes()));
            doc.add(summaryLine("Total Facturado (S/): " + report.totalFacturado()));
            doc.add(summaryLine("Ticket Promedio (S/): " + report.ticketPromedio()));

            PdfPTable table = new PdfPTable(3);
            table.setWidthPercentage(100);
            addHeaderCell(table, "Período");
            addHeaderCell(table, "Nro. Órdenes");
            addHeaderCell(table, "Ingresos (S/)");

            for (VentasPeriodoRow fila : report.filas()) {
                table.addCell(fila.periodo().toString());
                table.addCell(String.valueOf(fila.ordenes()));
                table.addCell(fila.monto().toPlainString());
            }
            doc.add(table);
        });
    }

    // ─── R2: Productos más vendidos ──────────────────────────────────────────────

    public byte[] exportTopProductos(TopProductosReport report) {
        return render(doc -> {
            doc.add(title("Reporte: Productos Más Vendidos"));

            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);
            addHeaderCell(table, "#");
            addHeaderCell(table, "SKU");
            addHeaderCell(table, "Producto");
            addHeaderCell(table, "Unidades Vendidas");
            addHeaderCell(table, "Ingresos (S/)");

            int rank = 1;
            for (TopProductoRow prod : report.productos()) {
                table.addCell(String.valueOf(rank++));
                table.addCell(prod.sku());
                table.addCell(prod.nombre());
                table.addCell(prod.unidades() != null ? prod.unidades().toString() : "0");
                table.addCell(prod.ingresos() != null ? prod.ingresos().toPlainString() : "0.00");
            }
            doc.add(table);
        });
    }

    // ─── R3: Kardex por producto ─────────────────────────────────────────────────

    public byte[] exportKardex(KardexReport report) {
        return render(doc -> {
            doc.add(title("Kardex: " + report.nombre()));
            doc.add(summaryLine("SKU: " + report.sku()));
            doc.add(summaryLine("Stock Actual: " + report.stockActual()));

            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);
            addHeaderCell(table, "Fecha");
            addHeaderCell(table, "Tipo");
            addHeaderCell(table, "Cantidad");
            addHeaderCell(table, "Motivo");
            addHeaderCell(table, "Referencia");

            for (KardexMovimientoRow mov : report.movimientos()) {
                table.addCell(DT_FMT.format(mov.fecha()));
                table.addCell(mov.tipo());
                table.addCell(String.valueOf(mov.cantidad()));
                table.addCell(mov.reason() != null ? mov.reason() : "");
                table.addCell(mov.reference() != null ? mov.reference() : "");
            }
            doc.add(table);
        });
    }

    // ─── R4: Listado de órdenes ──────────────────────────────────────────────────

    public byte[] exportOrdenes(OrdenesListadoReport report) {
        return render(doc -> {
            doc.add(title("Reporte: Listado de Órdenes"));
            doc.add(summaryLine("Total órdenes: " + report.total()));
            if (report.statusFiltro() != null) {
                doc.add(summaryLine("Estado: " + report.statusFiltro()));
            }

            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);
            addHeaderCell(table, "ID Orden");
            addHeaderCell(table, "Fecha");
            addHeaderCell(table, "Usuario (ID)");
            addHeaderCell(table, "Estado");
            addHeaderCell(table, "Total (S/)");

            for (OrderResponse orden : report.ordenes()) {
                table.addCell(String.valueOf(orden.id()));
                table.addCell(DT_FMT.format(orden.orderDate()));
                table.addCell(String.valueOf(orden.userId()));
                table.addCell(orden.status());
                table.addCell(orden.total() != null ? orden.total().toPlainString() : "0.00");
            }
            doc.add(table);
        });
    }

    // ─── private helpers ─────────────────────────────────────────────────────────

    /**
     * Renders a PDF document using the provided content consumer.
     * Opens Document + PdfWriter, runs body, closes Document, returns bytes.
     */
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
        Font f = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
        Paragraph p = new Paragraph(text, f);
        p.setAlignment(Element.ALIGN_CENTER);
        p.setSpacingAfter(10);
        return p;
    }

    private Paragraph summaryLine(String text) {
        Paragraph p = new Paragraph(text, FontFactory.getFont(FontFactory.HELVETICA, 10));
        p.setSpacingAfter(4);
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
