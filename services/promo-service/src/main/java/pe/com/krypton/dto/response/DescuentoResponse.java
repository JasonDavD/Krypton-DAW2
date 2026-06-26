package pe.com.krypton.dto.response;

import java.math.BigDecimal;

/** Resultado de aplicar un cupon: descuento calculado y monto final. */
public record DescuentoResponse(String code, BigDecimal discount, BigDecimal finalAmount) {
}
