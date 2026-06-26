package pe.com.krypton.exception;

/** Recurso inexistente. Se mapea a 404 Not Found. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
