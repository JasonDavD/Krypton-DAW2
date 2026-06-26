package pe.com.krypton.exception;

/** El usuario ya reseno este producto (una resena por usuario+producto). Se mapea a 409 Conflict. */
public class DuplicateReviewException extends RuntimeException {
    public DuplicateReviewException(String message) {
        super(message);
    }
}
