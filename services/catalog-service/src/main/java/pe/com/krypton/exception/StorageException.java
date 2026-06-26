package pe.com.krypton.exception;

/** Thrown when a storage I/O operation fails. Maps to HTTP 500. */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
