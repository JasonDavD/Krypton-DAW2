package pe.com.krypton.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.com.krypton.dto.request.CategoriaRequest;
import pe.com.krypton.dto.response.CategoriaResponse;
import pe.com.krypton.service.CategoriaService;

/** CRUD de categorías — solo ADMIN (autorización: /api/admin/** hasRole(ADMIN) en SecurityConfig). */
@RestController
@RequestMapping("/api/admin/categories")
public class AdminCategoriaController {

    private final CategoriaService categoryService;

    public AdminCategoriaController(CategoriaService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoriaResponse registrar(@Valid @RequestBody CategoriaRequest request) {
        return categoryService.registrar(request);
    }

    @PutMapping("/{id}")
    public CategoriaResponse actualizar(@PathVariable Long id, @Valid @RequestBody CategoriaRequest request) {
        return categoryService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {
        categoryService.eliminar(id);
    }
}
