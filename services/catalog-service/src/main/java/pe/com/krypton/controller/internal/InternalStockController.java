package pe.com.krypton.controller.internal;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.com.krypton.dto.request.StockMovementRequest;
import pe.com.krypton.service.StockService;

/**
 * Endpoints INTERNOS (servicio-a-servicio), pensados para Feign desde order-service.
 *
 * <p>NO se exponen por el api-gateway (no hay ruta /api/internal/** en el gateway), así que
 * solo se alcanzan dentro de la red de servicios. Igual exigen JWT autenticado (regla
 * {@code anyRequest().authenticated()} del SecurityConfig): order reenvía el token del usuario
 * que hace el checkout.
 */
@RestController
@RequestMapping("/api/internal/stock")
public class InternalStockController {

    private final StockService stockService;

    public InternalStockController(StockService stockService) {
        this.stockService = stockService;
    }

    /** Descuenta stock (SALIDA). 204 si OK; 422 si no alcanza; 404 si el producto no existe. */
    @PostMapping("/decrease")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decrease(@Valid @RequestBody StockMovementRequest request) {
        stockService.descontar(request);
    }

    /** Repone stock (ENTRADA) — compensación de la saga. 204 si OK; 404 si el producto no existe. */
    @PostMapping("/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restore(@Valid @RequestBody StockMovementRequest request) {
        stockService.restaurar(request);
    }
}
