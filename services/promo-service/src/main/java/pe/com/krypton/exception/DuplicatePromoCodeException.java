package pe.com.krypton.exception;

/** Ya existe un cupon con ese code (code es unico). Se mapea a 409 Conflict. */
public class DuplicatePromoCodeException extends RuntimeException {
    public DuplicatePromoCodeException(String message) {
        super(message);
    }
}
