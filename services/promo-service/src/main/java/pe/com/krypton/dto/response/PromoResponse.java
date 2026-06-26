package pe.com.krypton.dto.response;

import java.math.BigDecimal;

/** Vista de un cupon en la API. */
public record PromoResponse(Long id, String code, String type, BigDecimal value, boolean active) {
}
