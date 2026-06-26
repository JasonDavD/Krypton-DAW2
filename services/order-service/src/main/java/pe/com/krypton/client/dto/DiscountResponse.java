package pe.com.krypton.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/** Respuesta de promo-service: el descuento calculado y el monto final. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DiscountResponse(String code, BigDecimal discount, BigDecimal finalAmount) {
}
