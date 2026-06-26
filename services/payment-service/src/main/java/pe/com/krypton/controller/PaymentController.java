package pe.com.krypton.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.com.krypton.dto.request.ChargeRequest;
import pe.com.krypton.dto.response.PaymentResponse;
import pe.com.krypton.service.PaymentService;

/**
 * Cobros del checkout. Endpoint INTERNO: lo invoca order-service via Feign durante la
 * confirmacion de compra; NO se rutea por el api-gateway. Exige JWT autenticado
 * (cae bajo anyRequest().authenticated() del SecurityConfig — order reenvia el token del usuario).
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/charge")
    public PaymentResponse cobrar(@Valid @RequestBody ChargeRequest request) {
        return paymentService.cobrar(request);
    }
}
