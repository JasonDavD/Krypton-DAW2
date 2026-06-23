package pe.com.krypton.exception;

/**
 * No se puede degradar ni desactivar al último ADMIN activo (evita lockout).
 * Se mapea a 422 Unprocessable Entity.
 */
public class LastAdminException extends RuntimeException {
    public LastAdminException(String message) {
        super(message);
    }
}
