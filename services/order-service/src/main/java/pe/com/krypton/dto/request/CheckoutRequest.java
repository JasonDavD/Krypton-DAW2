package pe.com.krypton.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pe.com.krypton.entity.enums.TipoDocumento;

/** Datos de facturación para confirmar la compra. customerDoc: DNI (8) o RUC (11) dígitos. */
public record CheckoutRequest(
        @NotNull TipoDocumento documentType,
        @NotBlank @Size(max = 150) String customerName,
        @NotBlank @Pattern(regexp = "\\d{8}|\\d{11}", message = "DNI (8) o RUC (11) digitos") String customerDoc) {
}
