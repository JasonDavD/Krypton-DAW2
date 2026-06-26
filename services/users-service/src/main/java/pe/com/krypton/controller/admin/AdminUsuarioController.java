package pe.com.krypton.controller.admin;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.com.krypton.dto.request.CreateUsuarioRequest;
import pe.com.krypton.dto.request.UpdateRoleRequest;
import pe.com.krypton.dto.request.UpdateStatusRequest;
import pe.com.krypton.dto.response.UsuarioResponse;
import pe.com.krypton.service.UsuarioService;

/** Gestión de usuarios — solo ADMIN (autorización en SecurityConfig: /api/admin/**). */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUsuarioController {

    private final UsuarioService userService;

    public AdminUsuarioController(UsuarioService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UsuarioResponse> list() {
        return userService.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse registrar(@Valid @RequestBody CreateUsuarioRequest request) {
        return userService.registrar(request);
    }

    @PatchMapping("/{id}/role")
    public UsuarioResponse cambiarRol(@PathVariable Long id, @Valid @RequestBody UpdateRoleRequest request) {
        return userService.cambiarRol(id, request.role());
    }

    @PatchMapping("/{id}/status")
    public UsuarioResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest request) {
        return userService.cambiarEstado(id, request.active());
    }
}
