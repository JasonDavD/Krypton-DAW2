package pe.com.krypton.exception;

/**
 * El comprobante (boleta/factura) sólo se emite para pedidos PAGADOS.
 * Un pedido PENDIENTE (sin pago) o CANCELADA no tiene comprobante válido.
 * Se mapea a HTTP 409 Conflict en {@link GlobalExceptionHandler}.
 */
public class ComprobanteNotAvailableException extends RuntimeException {
    public ComprobanteNotAvailableException(String message) {
        super(message);
    }
}
