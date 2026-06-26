package pe.com.krypton.controller.admin;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.com.krypton.dto.request.CreatePromoRequest;
import pe.com.krypton.dto.response.PromoResponse;
import pe.com.krypton.service.PromoService;

/** Gestion de cupones — solo ADMIN (autorizacion: /api/admin/** hasRole(ADMIN) en SecurityConfig). */
@RestController
@RequestMapping("/api/admin/promos")
public class AdminPromoController {

    private final PromoService promoService;

    public AdminPromoController(PromoService promoService) {
        this.promoService = promoService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PromoResponse crear(@Valid @RequestBody CreatePromoRequest request) {
        return promoService.crear(request);
    }

    @GetMapping
    public List<PromoResponse> listar() {
        return promoService.listar();
    }
}
