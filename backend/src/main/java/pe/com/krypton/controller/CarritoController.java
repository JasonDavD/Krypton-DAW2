package pe.com.krypton.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
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

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CarritoController {

    private final CarritoService cartService;

    @GetMapping
    public CarritoResponse getCart(@AuthenticationPrincipal UserDetails principal) {
        return cartService.getCart(principal.getUsername());
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public CarritoResponse addItem(@AuthenticationPrincipal UserDetails principal,
                                @Valid @RequestBody ItemCarritoRequest request) {
        return cartService.addItem(principal.getUsername(), request);
    }

    @PutMapping("/items/{itemId}")
    public CarritoResponse updateItem(@AuthenticationPrincipal UserDetails principal,
                                   @PathVariable Long itemId,
                                   @Valid @RequestBody UpdateQuantityRequest request) {
        return cartService.updateItem(principal.getUsername(), itemId, request);
    }

    @DeleteMapping("/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeItem(@AuthenticationPrincipal UserDetails principal,
                           @PathVariable Long itemId) {
        cartService.removeItem(principal.getUsername(), itemId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearCart(@AuthenticationPrincipal UserDetails principal) {
        cartService.clearCart(principal.getUsername());
    }
}
