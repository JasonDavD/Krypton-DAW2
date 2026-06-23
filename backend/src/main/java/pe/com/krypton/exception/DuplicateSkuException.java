package pe.com.krypton.exception;

/** El SKU ya está registrado en otro producto. Se mapea a 409 Conflict. */
public class DuplicateSkuException extends RuntimeException {
    public DuplicateSkuException(String message) {
        super(message);
    }
}
