package pe.com.krypton.exception;

/** Cupón inexistente o inactivo al hacer checkout. Se mapea a 422 Unprocessable Entity. */
public class InvalidCouponException extends RuntimeException {
    public InvalidCouponException(String message) {
        super(message);
    }
}
