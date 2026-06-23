package pe.com.krypton.exception;

/**
 * Documento del receptor inválido para el tipo de comprobante:
 * FACTURA exige RUC (11 díg), BOLETA exige DNI (8 díg). Se mapea a 422.
 */
public class InvalidDocumentException extends RuntimeException {

    public InvalidDocumentException(String message) {
        super(message);
    }
}
