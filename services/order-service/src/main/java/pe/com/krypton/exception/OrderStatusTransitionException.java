package pe.com.krypton.exception;

/** Transición de estado de orden inválida. Se mapea a 422 Unprocessable Entity. */
public class OrderStatusTransitionException extends RuntimeException {
    public OrderStatusTransitionException(String message) {
        super(message);
    }
}
