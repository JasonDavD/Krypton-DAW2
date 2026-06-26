package pe.com.krypton.controller.store;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.com.krypton.dto.request.CheckoutRequest;
import pe.com.krypton.dto.response.OrdenResponse;
import pe.com.krypton.service.OrdenService;

/**
 * Pedidos del usuario autenticado. La identidad es el EMAIL del JWT:
 * se obtiene con {@code authentication.getName()}.
 */
@RestController
@RequestMapping("/api/orders")
public class OrdenController {

    private final OrdenService ordenService;

    public OrdenController(OrdenService ordenService) {
        this.ordenService = ordenService;
    }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public OrdenResponse confirmarCompra(@Valid @RequestBody CheckoutRequest request,
                                         Authentication authentication) {
        return ordenService.confirmarCompra(authentication.getName(), request);
    }

    @GetMapping
    public List<OrdenResponse> misOrdenes(Authentication authentication) {
        return ordenService.misOrdenes(authentication.getName());
    }

    @GetMapping("/{id}")
    public OrdenResponse miOrden(@PathVariable Long id, Authentication authentication) {
        return ordenService.miOrden(authentication.getName(), id);
    }
}
