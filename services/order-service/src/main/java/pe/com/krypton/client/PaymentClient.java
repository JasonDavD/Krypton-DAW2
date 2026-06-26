package pe.com.krypton.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import pe.com.krypton.client.dto.ChargeRequest;
import pe.com.krypton.client.dto.PaymentResponse;

/**
 * Cliente Feign hacia payment-service. order delega el COBRO acá; payment registra el pago
 * y responde APPROVED/DECLINED. El FeignAuthInterceptor reenvía el JWT del usuario.
 */
@FeignClient(name = "payment-service")
public interface PaymentClient {

    @PostMapping("/api/payments/charge")
    PaymentResponse charge(@RequestBody ChargeRequest request);
}
