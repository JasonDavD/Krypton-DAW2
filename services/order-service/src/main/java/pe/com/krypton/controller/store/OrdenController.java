package pe.com.krypton.controller.store;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.com.krypton.dto.request.CheckoutRequest;
import pe.com.krypton.dto.request.PaymentRequest;
import pe.com.krypton.dto.response.OrdenResponse;
import pe.com.krypton.event.OrderCreatedEvent;
import pe.com.krypton.messaging.OrderEventPublisher;
import pe.com.krypton.service.OrdenService;

/**
 * Pedidos del usuario autenticado. La identidad es el EMAIL del JWT:
 * se obtiene con {@code authentication.getName()}.
 */
@RestController
@RequestMapping("/api/orders")
public class OrdenController {

    private final OrdenService ordenService;
    private final OrderEventPublisher eventPublisher;

    public OrdenController(OrdenService ordenService, OrderEventPublisher eventPublisher) {
        this.ordenService = ordenService;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public OrdenResponse confirmarCompra(@Valid @RequestBody CheckoutRequest request,
                                         Authentication authentication) {
        OrdenResponse orden = ordenService.confirmarCompra(authentication.getName(), request);
        // Evento ASÍNCRONO: la orden YA se confirmó (commiteó). Publicamos y seguimos; si el
        // checkout hubiera fallado, esta línea no se alcanza (no se notifica nada).
        eventPublisher.publishOrderCreated(
                new OrderCreatedEvent(orden.id(), orden.userEmail(), orden.total()));
        return orden;
    }

    @GetMapping
    public List<OrdenResponse> misOrdenes(Authentication authentication) {
        return ordenService.misOrdenes(authentication.getName());
    }

    @GetMapping("/{id}")
    public OrdenResponse miOrden(@PathVariable Long id, Authentication authentication) {
        return ordenService.miOrden(authentication.getName(), id);
    }

    @PostMapping("/{id}/pay")
    public OrdenResponse pagar(@PathVariable Long id,
                               @Valid @RequestBody PaymentRequest request,
                               Authentication authentication) {
        return ordenService.pagar(authentication.getName(), id, request);
    }

    /** GET /api/orders/{id}/comprobante → 200 application/pdf (boleta/factura del propio pedido pagado). */
    @GetMapping(value = "/{id}/comprobante", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> comprobante(@PathVariable Long id, Authentication authentication) {
        byte[] pdf = ordenService.miComprobantePdf(authentication.getName(), id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(
                ContentDisposition.attachment().filename("comprobante_" + id + ".pdf").build());
        headers.setContentLength(pdf.length);
        return ResponseEntity.ok().headers(headers).body(pdf);
    }
}
