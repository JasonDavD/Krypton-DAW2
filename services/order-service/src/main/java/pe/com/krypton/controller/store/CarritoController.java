package pe.com.krypton.controller.store;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.com.krypton.dto.request.ItemCarritoRequest;
import pe.com.krypton.dto.request.UpdateQuantityRequest;
import pe.com.krypton.dto.response.CarritoResponse;
import pe.com.krypton.service.CarritoService;

/**
 * Carrito del usuario autenticado. La identidad es el EMAIL del JWT:
 * se obtiene con {@code authentication.getName()} (el principal es un String, no UserDetails).
 */
@RestController
@RequestMapping("/api/cart")
public class CarritoController {

    private final CarritoService cartService;

    public CarritoController(CarritoService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CarritoResponse obtenerCarrito(Authentication authentication) {
        return cartService.obtenerCarrito(authentication.getName());
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public CarritoResponse agregarItem(@Valid @RequestBody ItemCarritoRequest request,
                                       Authentication authentication) {
        return cartService.agregarItem(authentication.getName(), request);
    }

    @PutMapping("/items/{itemId}")
    public CarritoResponse actualizarItem(@PathVariable Long itemId,
                                          @Valid @RequestBody UpdateQuantityRequest request,
                                          Authentication authentication) {
        return cartService.actualizarItem(authentication.getName(), itemId, request);
    }

    @DeleteMapping("/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void quitarItem(@PathVariable Long itemId, Authentication authentication) {
        cartService.quitarItem(authentication.getName(), itemId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void vaciarCarrito(Authentication authentication) {
        cartService.vaciarCarrito(authentication.getName());
    }
}
