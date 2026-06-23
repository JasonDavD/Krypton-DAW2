package pe.com.krypton.report;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import pe.com.krypton.dto.response.OrderResponse;
import pe.com.krypton.dto.response.report.KardexMovimientoRow;
import pe.com.krypton.dto.response.report.KardexReport;
import pe.com.krypton.dto.response.report.OrdenesListadoReport;
import pe.com.krypton.dto.response.report.TopProductoRow;
import pe.com.krypton.dto.response.report.TopProductosReport;
import pe.com.krypton.dto.response.report.VentasPeriodoRow;
import pe.com.krypton.dto.response.report.VentasPorPeriodoReport;

/** Genera archivos Excel (XLSX) a partir de los DTOs de reporte. Un método por reporte. */
@Component
public class ExcelExporter {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(LIMA);

    // ─── R1: Ventas por período ──────────────────────────────────────────────────

    public byte[] exportVentas(VentasPorPeriodoReport report) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("Ventas por Período");
            XSSFCellStyle header = headerStyle(wb);

            // Summary rows
            int row = 0;
            row = writeSummaryRow(sheet, row, "Total Órdenes", report.totalOrdenes());
            row = writeSummaryRow(sheet, row, "Total Facturado (S/)", report.totalFacturado());
            row = writeSummaryRow(sheet, row, "Ticket Promedio (S/)", report.ticketPromedio());
            row++; // blank separator

            // Header row
            XSSFRow headerRow = sheet.createRow(row++);
            writeHeaderCell(headerRow, 0, "Período", header);
            writeHeaderCell(headerRow, 1, "Nro. Órdenes", header);
            writeHeaderCell(headerRow, 2, "Ingresos (S/)", header);
            writeHeaderCell(headerRow, 3, "Ticket Promedio", header);

            // Data rows
            for (VentasPeriodoRow fila : report.filas()) {
                XSSFRow dataRow = sheet.createRow(row++);
                dataRow.createCell(0).setCellValue(fila.periodo().toString());
                dataRow.createCell(1).setCellValue(fila.ordenes());
                dataRow.createCell(2).setCellValue(fila.monto().doubleValue());
                // ticket per row = monto / ordenes (avoid division by zero)
                double ticket = fila.ordenes() > 0
                        ? fila.monto().doubleValue() / fila.ordenes()
                        : 0.0;
                dataRow.createCell(3).setCellValue(ticket);
            }

            autosize(sheet, 4);
            return toBytes(wb);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ─── R2: Productos más vendidos ──────────────────────────────────────────────

    public byte[] exportTopProductos(TopProductosReport report) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("Productos Más Vendidos");
            XSSFCellStyle header = headerStyle(wb);

            int row = 0;

            // Header row
            XSSFRow headerRow = sheet.createRow(row++);
            writeHeaderCell(headerRow, 0, "#", header);
            writeHeaderCell(headerRow, 1, "SKU", header);
            writeHeaderCell(headerRow, 2, "Producto", header);
            writeHeaderCell(headerRow, 3, "Unidades Vendidas", header);
            writeHeaderCell(headerRow, 4, "Ingresos (S/)", header);

            // Data rows
            int rank = 1;
            for (TopProductoRow prod : report.productos()) {
                XSSFRow dataRow = sheet.createRow(row++);
                dataRow.createCell(0).setCellValue(rank++);
                dataRow.createCell(1).setCellValue(prod.sku());
                dataRow.createCell(2).setCellValue(prod.nombre());
                // unidades is Long (boxed); unbox safely
                dataRow.createCell(3).setCellValue(
                        prod.unidades() != null ? prod.unidades().longValue() : 0L);
                dataRow.createCell(4).setCellValue(
                        prod.ingresos() != null ? prod.ingresos().doubleValue() : 0.0);
            }

            autosize(sheet, 5);
            return toBytes(wb);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ─── R3: Kardex por producto ─────────────────────────────────────────────────

    public byte[] exportKardex(KardexReport report) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("Kardex");
            XSSFCellStyle headerSt = headerStyle(wb);

            int row = 0;
            row = writeSummaryRow(sheet, row, "SKU", report.sku());
            row = writeSummaryRow(sheet, row, "Producto", report.nombre());
            row = writeSummaryRow(sheet, row, "Stock Actual", report.stockActual());
            row++; // blank separator

            // Header row
            XSSFRow headerRow = sheet.createRow(row++);
            writeHeaderCell(headerRow, 0, "Fecha", headerSt);
            writeHeaderCell(headerRow, 1, "Tipo", headerSt);
            writeHeaderCell(headerRow, 2, "Cantidad", headerSt);
            writeHeaderCell(headerRow, 3, "Motivo", headerSt);
            writeHeaderCell(headerRow, 4, "Referencia", headerSt);

            // Data rows
            for (KardexMovimientoRow mov : report.movimientos()) {
                XSSFRow dataRow = sheet.createRow(row++);
                dataRow.createCell(0).setCellValue(DT_FMT.format(mov.fecha()));
                dataRow.createCell(1).setCellValue(mov.tipo());
                dataRow.createCell(2).setCellValue(mov.cantidad());
                dataRow.createCell(3).setCellValue(mov.reason() != null ? mov.reason() : "");
                dataRow.createCell(4).setCellValue(mov.reference() != null ? mov.reference() : "");
            }

            autosize(sheet, 5);
            return toBytes(wb);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ─── R4: Listado de órdenes ──────────────────────────────────────────────────

    public byte[] exportOrdenes(OrdenesListadoReport report) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("Listado de Órdenes");
            XSSFCellStyle headerSt = headerStyle(wb);

            int row = 0;
            row = writeSummaryRow(sheet, row, "Total órdenes", report.total());
            if (report.statusFiltro() != null) {
                row = writeSummaryRow(sheet, row, "Estado filtro", report.statusFiltro());
            }
            row++; // blank separator

            // Header row
            XSSFRow headerRow = sheet.createRow(row++);
            writeHeaderCell(headerRow, 0, "ID Orden", headerSt);
            writeHeaderCell(headerRow, 1, "Fecha", headerSt);
            writeHeaderCell(headerRow, 2, "Usuario (ID)", headerSt);
            writeHeaderCell(headerRow, 3, "Estado", headerSt);
            writeHeaderCell(headerRow, 4, "Total (S/)", headerSt);

            // Data rows
            for (OrderResponse orden : report.ordenes()) {
                XSSFRow dataRow = sheet.createRow(row++);
                dataRow.createCell(0).setCellValue(orden.id());
                dataRow.createCell(1).setCellValue(DT_FMT.format(orden.orderDate()));
                dataRow.createCell(2).setCellValue(orden.userId());
                dataRow.createCell(3).setCellValue(orden.status());
                dataRow.createCell(4).setCellValue(
                        orden.total() != null ? orden.total().doubleValue() : 0.0);
            }

            autosize(sheet, 5);
            return toBytes(wb);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ─── private helpers ─────────────────────────────────────────────────────────

    /** Creates a bold, grey-filled header cell style. */
    private XSSFCellStyle headerStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private void writeHeaderCell(XSSFRow row, int col, String value, XSSFCellStyle style) {
        var cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private int writeSummaryRow(XSSFSheet sheet, int rowIdx, String label, Object value) {
        XSSFRow row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value != null ? value.toString() : "");
        return rowIdx + 1;
    }

    private void autosize(XSSFSheet sheet, int cols) {
        for (int i = 0; i < cols; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private byte[] toBytes(XSSFWorkbook wb) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        wb.write(baos);
        return baos.toByteArray();
    }
}
