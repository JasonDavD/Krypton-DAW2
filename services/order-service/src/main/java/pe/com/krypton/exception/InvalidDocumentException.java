package pe.com.krypton.exception;

/** Documento de facturación inválido para el tipo de comprobante. Se mapea a 422. */
public class InvalidDocumentException extends RuntimeException {
    public InvalidDocumentException(String message) {
        super(message);
    }
}
