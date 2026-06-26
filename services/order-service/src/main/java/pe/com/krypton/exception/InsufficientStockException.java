package pe.com.krypton.exception;

/** Stock insuficiente para la cantidad pedida. Se mapea a 422. */
public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String message) {
        super(message);
    }
}
