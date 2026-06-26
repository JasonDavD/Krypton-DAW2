package pe.com.krypton.exception;

/** Stock insuficiente para descontar en el checkout. Se mapea a 422 Unprocessable Entity. */
public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String message) {
        super(message);
    }
}
