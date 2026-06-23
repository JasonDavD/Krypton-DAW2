package pe.com.krypton.exception;

/** Transición de estado de orden inválida (ej. pagar una orden ya CONFIRMADA). Se mapea a 422. */
public class OrderStatusTransitionException extends RuntimeException {

    public OrderStatusTransitionException(String message) {
        super(message);
    }
}
