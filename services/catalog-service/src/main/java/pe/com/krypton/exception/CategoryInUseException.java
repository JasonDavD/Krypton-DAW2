package pe.com.krypton.exception;

/** La categoría tiene productos asociados y no puede eliminarse. Se mapea a 409 Conflict. */
public class CategoryInUseException extends RuntimeException {
    public CategoryInUseException(String message) {
        super(message);
    }
}
