package pe.com.krypton.service.impl;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.krypton.dto.request.ChargeRequest;
import pe.com.krypton.dto.response.PaymentResponse;
import pe.com.krypton.entity.Pago;
import pe.com.krypton.entity.enums.PaymentStatus;
import pe.com.krypton.repository.PagoRepository;
import pe.com.krypton.service.PaymentService;

/**
 * Procesamiento de cobros del checkout.
 *
 * <p>DEMO: simulamos la pasarela — el cobro SIEMPRE se aprueba. En produccion, aca iria
 * la integracion real con la pasarela (Niubiz/Culqi/Stripe): tokenizar, llamar a su API,
 * y mapear su respuesta a APPROVED/DECLINED.
 */
@Service
public class PaymentServiceImpl implements PaymentService {

    private final PagoRepository pagoRepository;

    public PaymentServiceImpl(PagoRepository pagoRepository) {
        this.pagoRepository = pagoRepository;
    }

    @Override
    @Transactional
    public PaymentResponse cobrar(ChargeRequest request) {
        Pago pago = new Pago();
        pago.setOrderId(request.orderId());
        pago.setAmount(request.amount());
        pago.setMethod(request.method());
        pago.setStatus(PaymentStatus.APPROVED); // SIMULAMOS: siempre se aprueba en el demo
        pago.setCreatedAt(Instant.now());
        pagoRepository.save(pago);
        return new PaymentResponse(pago.getId(), pago.getStatus().name());
    }
}
