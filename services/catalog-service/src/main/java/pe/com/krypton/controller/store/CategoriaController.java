package pe.com.krypton.controller.store;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.com.krypton.dto.response.CategoriaResponse;
import pe.com.krypton.service.CategoriaService;

/** Endpoints públicos de categorías — solo lectura. Seguridad: GET permitAll en SecurityConfig. */
@RestController
@RequestMapping("/api/categories")
public class CategoriaController {

    private final CategoriaService categoryService;

    public CategoriaController(CategoriaService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public List<CategoriaResponse> listar() {
        return categoryService.listar();
    }

    @GetMapping("/{id}")
    public CategoriaResponse buscarPorId(@PathVariable Long id) {
        return categoryService.buscarPorId(id);
    }
}
