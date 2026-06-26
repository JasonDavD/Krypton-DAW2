package pe.com.krypton.exception;

/** Checkout sobre un carrito vacío. Se mapea a 400 Bad Request. */
public class EmptyCartException extends RuntimeException {
    public EmptyCartException(String message) {
        super(message);
    }
}
