package pe.com.krypton.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import pe.com.krypton.client.dto.ProductoResponse;

/**
 * Cliente Feign hacia catalog-service. {@code name="catalog-service"} = el nombre en Eureka:
 * Feign le pregunta a Eureka donde esta y balancea (igual que lb:// en el gateway).
 *
 * <p>El {@link pe.com.krypton.config.FeignAuthInterceptor} reenvia el header Authorization,
 * asi que estas llamadas viajan con el JWT del usuario que crea la resena.
 */
@FeignClient(name = "catalog-service")
public interface CatalogClient {

    /** Trae el producto para validar que existe antes de guardar la resena (Feign 404 si no). */
    @GetMapping("/api/products/{id}")
    ProductoResponse getProduct(@PathVariable("id") Long id);
}
