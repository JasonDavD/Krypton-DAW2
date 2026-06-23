package pe.com.krypton.controller;

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
import pe.com.krypton.dto.request.CreateUserRequest;
import pe.com.krypton.dto.request.UpdateRoleRequest;
import pe.com.krypton.dto.request.UpdateStatusRequest;
import pe.com.krypton.dto.response.UserResponse;
import pe.com.krypton.service.UserService;

/** Gestión de usuarios — solo ADMIN (autorización en SecurityConfig: /api/admin/**). */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserResponse> list() {
        return userService.listAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request);
    }

    @PatchMapping("/{id}/role")
    public UserResponse changeRole(@PathVariable Long id, @Valid @RequestBody UpdateRoleRequest request) {
        return userService.changeRole(id, request.role());
    }

    @PatchMapping("/{id}/status")
    public UserResponse setStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest request) {
        return userService.setStatus(id, request.active());
    }
}
