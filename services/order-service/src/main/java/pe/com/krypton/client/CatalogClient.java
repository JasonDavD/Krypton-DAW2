package pe.com.krypton.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import pe.com.krypton.client.dto.ProductoResponse;
import pe.com.krypton.client.dto.StockMovementRequest;
import pe.com.krypton.dto.response.report.KardexReport;

/**
 * Cliente Feign hacia catalog-service. {@code name="catalog-service"} = el nombre en Eureka:
 * Feign le pregunta a Eureka donde esta y balancea (igual que lb:// en el gateway).
 *
 * <p>El {@link pe.com.krypton.config.FeignAuthInterceptor} reenvia el header Authorization,
 * asi que estas llamadas viajan con el JWT del usuario que hace el checkout.
 */
@FeignClient(name = "catalog-service")
public interface CatalogClient {

    /** Trae el producto (precio VIVO, stock, estado) para mostrar el carrito y congelar el precio. */
    @GetMapping("/api/products/{id}")
    ProductoResponse getProduct(@PathVariable("id") Long id);

    /** Descuenta stock (SALIDA). Lanza FeignException 422 si no alcanza. */
    @PostMapping("/api/internal/stock/decrease")
    void decreaseStock(@RequestBody StockMovementRequest request);

    /** Repone stock (ENTRADA) — compensacion de la saga. */
    @PostMapping("/api/internal/stock/restore")
    void restoreStock(@RequestBody StockMovementRequest request);

    /**
     * Kardex de un producto (reporte admin). catalog es dueño de stock_movement; se pide por Feign.
     * start/end viajan como String ISO-8601 (robusto: no depende de converters de Instant en query).
     */
    @GetMapping("/api/internal/reports/kardex")
    KardexReport kardex(@RequestParam("productId") Long productId,
                        @RequestParam(value = "start", required = false) String start,
                        @RequestParam(value = "end", required = false) String end);
}
