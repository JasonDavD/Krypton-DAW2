package pe.com.krypton.exception;

/** El nombre de categoría ya está en uso. Se mapea a 409 Conflict. */
public class DuplicateCategoryNameException extends RuntimeException {
    public DuplicateCategoryNameException(String message) {
        super(message);
    }
}
