package pe.com.krypton.client.dto;

import java.math.BigDecimal;

/** Pedido de aplicación de cupón a promo-service: el código y el monto sobre el que descontar. */
public record ApplyPromoRequest(String code, BigDecimal amount) {
}
