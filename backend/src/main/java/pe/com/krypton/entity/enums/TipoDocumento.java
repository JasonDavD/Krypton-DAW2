package pe.com.krypton.entity.enums;

/**
 * Tipo de comprobante de pago. BOLETA para consumidor final (DNI); FACTURA para
 * cliente con RUC (desglosa base + IGV como crédito fiscal). Ambos llevan IGV.
 */
public enum TipoDocumento {
    BOLETA,
    FACTURA
}
