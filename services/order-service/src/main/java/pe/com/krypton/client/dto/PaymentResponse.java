package pe.com.krypton.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Respuesta de payment-service: el id del pago y su estado (APPROVED / DECLINED). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentResponse(Long paymentId, String status) {
}
