package pe.com.krypton.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import pe.com.krypton.client.dto.ApplyPromoRequest;
import pe.com.krypton.client.dto.DiscountResponse;

/**
 * Cliente Feign hacia promo-service. En el checkout, order le pide a promo el descuento de un
 * cupón sobre el subtotal. 422 si el cupón es inválido/inactivo (lo traduce order a un mensaje claro).
 */
@FeignClient(name = "promo-service")
public interface PromoClient {

    @PostMapping("/api/promos/apply")
    DiscountResponse applyPromo(@RequestBody ApplyPromoRequest request);
}
