package pe.com.krypton.exception;

/** El carrito está vacío al intentar realizar el checkout. Se mapea a 400 Bad Request. */
public class EmptyCartException extends RuntimeException {

    public EmptyCartException(String message) {
        super(message);
    }
}
