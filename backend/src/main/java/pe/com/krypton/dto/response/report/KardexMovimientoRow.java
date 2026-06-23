package pe.com.krypton.dto.response.report;

import java.time.Instant;

/** Fila del reporte R3: movimiento de inventario (kardex) de un producto. */
public record KardexMovimientoRow(
        Instant fecha,
        String tipo,
        int cantidad,
        String reason,
        String reference) {
}
