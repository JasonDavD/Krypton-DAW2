package pe.com.krypton.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
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
import pe.com.krypton.service.OrdenService;

/**
 * Client-facing order endpoints.
 * Authorization: anyRequest().authenticated() via SecurityConfig (JWT required).
 * Principal is resolved via @AuthenticationPrincipal — mirrors CarritoController.
 * Satisfies REQ-OM-01..REQ-OM-09, REQ-OM-13.
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrdenController {

    private final OrdenService orderService;

    /** POST /api/orders/checkout → 201 OrdenResponse */
    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public OrdenResponse checkout(@AuthenticationPrincipal UserDetails principal,
                                  @Valid @RequestBody CheckoutRequest request) {
        return orderService.checkout(principal.getUsername(), request);
    }

    /** GET /api/orders → 200 List<OrdenResponse> (only the caller's orders, newest first) */
    @GetMapping
    public List<OrdenResponse> getMyOrders(@AuthenticationPrincipal UserDetails principal) {
        return orderService.getMyOrders(principal.getUsername());
    }

    /** GET /api/orders/{id} → 200 OrdenResponse (404 if not owner or not found) */
    @GetMapping("/{id}")
    public OrdenResponse getMyOrder(@AuthenticationPrincipal UserDetails principal,
                                    @PathVariable Long id) {
        return orderService.getMyOrder(principal.getUsername(), id);
    }

    /** POST /api/orders/{id}/pay → 200 OrdenResponse (simulated payment: PENDIENTE → CONFIRMADA) */
    @PostMapping("/{id}/pay")
    public OrdenResponse pay(@AuthenticationPrincipal UserDetails principal,
                             @PathVariable Long id,
                             @Valid @RequestBody PaymentRequest request) {
        return orderService.pay(principal.getUsername(), id, request);
    }

    /** GET /api/orders/{id}/comprobante → 200 application/pdf (boleta/factura del propio pedido pagado). */
    @GetMapping(value = "/{id}/comprobante", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> comprobante(@AuthenticationPrincipal UserDetails principal,
                                              @PathVariable Long id) {
        byte[] pdf = orderService.getMyComprobantePdf(principal.getUsername(), id);
        return pdfAttachment(pdf, "comprobante_" + id + ".pdf");
    }

    private ResponseEntity<byte[]> pdfAttachment(byte[] body, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(body.length);
        return ResponseEntity.ok().headers(headers).body(body);
    }
}
