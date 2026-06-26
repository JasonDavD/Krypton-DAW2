package pe.com.krypton.service;

import pe.com.krypton.dto.request.ChargeRequest;
import pe.com.krypton.dto.response.PaymentResponse;

/** Operaciones de cobro. */
public interface PaymentService {

    /** Procesa el cobro de un pedido y devuelve el resultado (paymentId + status). */
    PaymentResponse cobrar(ChargeRequest request);
}
