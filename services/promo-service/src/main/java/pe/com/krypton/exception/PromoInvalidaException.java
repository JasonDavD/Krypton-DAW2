package pe.com.krypton.exception;

/** El cupon no se puede aplicar (inexistente o inactivo). Se mapea a 422 Unprocessable Entity. */
public class PromoInvalidaException extends RuntimeException {
    public PromoInvalidaException(String message) {
        super(message);
    }
}
