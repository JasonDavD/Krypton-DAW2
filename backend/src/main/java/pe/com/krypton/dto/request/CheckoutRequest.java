package pe.com.krypton.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pe.com.krypton.model.enums.DocumentType;

/**
 * Datos del comprobante para el checkout. El total NO viene del cliente: lo calcula
 * el backend (subtotal + envío, IGV desglosado). El cliente sólo elige el tipo de
 * comprobante y los datos del receptor.
 *
 * La regla condicional (BOLETA → DNI 8 díg, FACTURA → RUC 11 díg) se valida en el
 * service; bean validation sólo cubre aquí la forma genérica del documento.
 */
public record CheckoutRequest(
        @NotNull DocumentType documentType,
        @NotBlank @Size(max = 150) String customerName,
        @NotBlank
        @Pattern(regexp = "\\d{8}|\\d{11}",
                message = "El documento debe tener 8 dígitos (DNI) u 11 (RUC)")
        String customerDoc) {
}
