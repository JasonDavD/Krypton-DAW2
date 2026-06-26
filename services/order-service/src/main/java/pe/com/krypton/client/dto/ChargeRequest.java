package pe.com.krypton.client.dto;

import java.math.BigDecimal;

/** Pedido de cobro que order envía a payment-service vía Feign. */
public record ChargeRequest(Long orderId, BigDecimal amount, String method) {
}
