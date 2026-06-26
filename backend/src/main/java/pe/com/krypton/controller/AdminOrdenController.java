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
import pe.com.krypton.dto.response.OrdenResponse;
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
     * GET /api/admin/orders → 200 PageResponse<OrdenResponse> (paginado).
     * Filtros opcionales: {@code status}, y rango de fecha [from, to) en ISO-8601.
     */
    @GetMapping
    public PageResponse<OrdenResponse> listarOrdenes(
            @RequestParam(required = false) EstadoOrden status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            Pageable pageable) {
        return orderService.listarOrdenes(status, from, to, pageable);
    }

    /** GET /api/admin/orders/{id} → 200 OrdenResponse (any user's order, 404 if not found) */
    @GetMapping("/{id}")
    public OrdenResponse obtenerOrden(@PathVariable Long id) {
        return orderService.obtenerOrden(id);
    }

    /** PUT /api/admin/orders/{id}/status → 200 OrdenResponse (transición validada; 422 si es ilegal) */
    @PutMapping("/{id}/status")
    public OrdenResponse actualizarEstado(@PathVariable Long id,
                                      @Valid @RequestBody OrderStatusUpdateRequest request) {
        return orderService.actualizarEstado(id, request.status());
    }

    /** GET /api/admin/orders/{id}/comprobante → 200 application/pdf (boleta/factura de cualquier pedido pagado). */
    @GetMapping(value = "/{id}/comprobante", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> comprobante(@PathVariable Long id) {
        byte[] pdf = orderService.comprobantePdf(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(
                ContentDisposition.attachment().filename("comprobante_" + id + ".pdf").build());
        headers.setContentLength(pdf.length);
        return ResponseEntity.ok().headers(headers).body(pdf);
    }
}
