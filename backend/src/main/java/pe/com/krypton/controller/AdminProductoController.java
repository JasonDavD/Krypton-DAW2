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
import pe.com.krypton.dto.request.ProductoRequest;
import pe.com.krypton.dto.response.ProductoResponse;
import pe.com.krypton.service.ProductoService;

/** CRUD de productos — solo ADMIN (autorización: /api/admin/** hasRole(ADMIN) en SecurityConfig). */
@RestController
@RequestMapping("/api/admin/products")
public class AdminProductoController {

    private final ProductoService productService;

    public AdminProductoController(ProductoService productService) {
        this.productService = productService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductoResponse registrar(@Valid @RequestBody ProductoRequest request) {
        return productService.registrar(request);
    }

    @PutMapping("/{id}")
    public ProductoResponse actualizar(@PathVariable Long id, @Valid @RequestBody ProductoRequest request) {
        return productService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {
        productService.eliminar(id);
    }
}
