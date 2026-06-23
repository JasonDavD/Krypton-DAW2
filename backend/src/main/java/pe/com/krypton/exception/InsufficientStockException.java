package pe.com.krypton.exception;

/** Stock insuficiente al agregar/actualizar un ítem del carrito. Se mapea a 422. */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String message) {
        super(message);
    }
}
