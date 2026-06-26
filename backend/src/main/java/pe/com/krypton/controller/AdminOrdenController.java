package pe.com.krypton.controller;

import jakarta.validation.Valid;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.com.krypton.dto.request.OrderStatusUpdateRequest;
import pe.com.krypton.dto.response.OrderResponse;
import pe.com.krypton.dto.response.PageResponse;
import pe.com.krypton.entity.enums.EstadoOrden;
import pe.com.krypton.service.OrdenService;

/**
 * Admin order management endpoints.
 * Authorization: /api/admin/** → hasRole("ADMIN") enforced by SecurityConfig.
 * No @AuthenticationPrincipal needed — admin context is not ownership-scoped.
 * Satisfies REQ-OM-10..REQ-OM-12, REQ-OM-13.
 */
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class AdminOrdenController {

    private final OrdenService orderService;

    /**
     * GET /api/admin/orders → 200 PageResponse<OrderResponse> (paginado).
     * Filtros opcionales: {@code status}, y rango de fecha [from, to) en ISO-8601.
     */
    @GetMapping
    public PageResponse<OrderResponse> getAllOrders(
            @RequestParam(required = false) EstadoOrden status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            Pageable pageable) {
        return orderService.getAllOrders(status, from, to, pageable);
    }

    /** GET /api/admin/orders/{id} → 200 OrderResponse (any user's order, 404 if not found) */
    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable Long id) {
        return orderService.getOrder(id);
    }

    /** PUT /api/admin/orders/{id}/status → 200 OrderResponse (transición validada; 422 si es ilegal) */
    @PutMapping("/{id}/status")
    public OrderResponse updateStatus(@PathVariable Long id,
                                      @Valid @RequestBody OrderStatusUpdateRequest request) {
        return orderService.updateStatus(id, request.status());
    }

    /** GET /api/admin/orders/{id}/comprobante → 200 application/pdf (boleta/factura de cualquier pedido pagado). */
    @GetMapping(value = "/{id}/comprobante", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> comprobante(@PathVariable Long id) {
        byte[] pdf = orderService.getComprobantePdf(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(
                ContentDisposition.attachment().filename("comprobante_" + id + ".pdf").build());
        headers.setContentLength(pdf.length);
        return ResponseEntity.ok().headers(headers).body(pdf);
    }
}
