package pe.com.krypton.dto.response.report;

import java.time.Instant;

/** Fila del kardex: un movimiento de inventario de un producto. tipo: ENTRADA | SALIDA. */
public record KardexMovimientoRow(
        Instant fecha,
        String tipo,
        int cantidad,
        String reason,
        String reference) {
}
