package pe.com.krypton.exception;

/** El pago fue rechazado por payment-service. Se mapea a 402 Payment Required. */
public class PaymentDeclinedException extends RuntimeException {
    public PaymentDeclinedException(String message) {
        super(message);
    }
}
