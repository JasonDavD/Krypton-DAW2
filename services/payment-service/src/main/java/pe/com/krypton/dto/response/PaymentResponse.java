package pe.com.krypton.dto.response;

/** Resultado del cobro devuelto a order-service. */
public record PaymentResponse(Long paymentId, String status) {
}
