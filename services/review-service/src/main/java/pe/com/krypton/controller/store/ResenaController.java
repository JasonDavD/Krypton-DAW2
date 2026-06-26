package pe.com.krypton.controller.store;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.com.krypton.dto.request.CreateResenaRequest;
import pe.com.krypton.dto.response.ResenaListResponse;
import pe.com.krypton.dto.response.ResenaResponse;
import pe.com.krypton.service.ResenaService;

/**
 * Resenas de productos. Crear exige login (la identidad es el EMAIL del JWT, via
 * {@code authentication.getName()}); ver resenas es publico.
 */
@RestController
@RequestMapping("/api/reviews")
public class ResenaController {

    private final ResenaService resenaService;

    public ResenaController(ResenaService resenaService) {
        this.resenaService = resenaService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResenaResponse crear(@Valid @RequestBody CreateResenaRequest request,
                                Authentication authentication) {
        return resenaService.crear(authentication.getName(), request);
    }

    @GetMapping
    public ResenaListResponse listarPorProducto(@RequestParam Long productId) {
        return resenaService.listarPorProducto(productId);
    }
}
