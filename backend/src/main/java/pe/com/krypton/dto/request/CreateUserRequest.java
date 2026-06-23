package pe.com.krypton.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import pe.com.krypton.model.enums.Role;

/** Alta de usuario por un ADMIN: el rol es elegible (CLIENTE o ADMIN). */
public record CreateUserRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        @NotBlank String password,
        @NotNull Role role) {
}
