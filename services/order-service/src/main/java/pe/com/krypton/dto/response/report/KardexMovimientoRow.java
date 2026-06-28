package pe.com.krypton.dto.response.report;

import java.time.Instant;

/** Fila del kardex (contrato Feign con catalog). tipo: ENTRADA | SALIDA. */
public record KardexMovimientoRow(
        Instant fecha,
        String tipo,
        int cantidad,
        String reason,
        String reference) {
}
