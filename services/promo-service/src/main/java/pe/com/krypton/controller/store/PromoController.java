package pe.com.krypton.controller.store;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.com.krypton.dto.request.AplicarPromoRequest;
import pe.com.krypton.dto.response.DescuentoResponse;
import pe.com.krypton.service.PromoService;

/**
 * Aplicacion de cupones para el cliente. Exige login (cae bajo anyRequest().authenticated()
 * del SecurityConfig). Devuelve el descuento calculado y el monto final.
 */
@RestController
@RequestMapping("/api/promos")
public class PromoController {

    private final PromoService promoService;

    public PromoController(PromoService promoService) {
        this.promoService = promoService;
    }

    @PostMapping("/apply")
    public DescuentoResponse aplicar(@Valid @RequestBody AplicarPromoRequest request) {
        return promoService.aplicar(request);
    }
}
