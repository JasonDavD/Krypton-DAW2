package pe.com.krypton.controller.admin;

import jakarta.validation.Valid;
import java.time.Instant;
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
 * Gestión de pedidos para el admin. /api/admin/** -> ROLE_ADMIN (SecurityConfig).
 * No usa identidad: el admin ve/gestiona CUALQUIER orden (no ownership-scoped).
 */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrdenController {

    private final OrdenService ordenService;

    public AdminOrdenController(OrdenService ordenService) {
        this.ordenService = ordenService;
    }

    /** GET /api/admin/orders → 200 PageResponse paginado, con filtros opcionales status/from/to. */
    @GetMapping
    public PageResponse<OrdenResponse> listarOrdenes(
            @RequestParam(required = false) EstadoOrden status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            Pageable pageable) {
        return ordenService.listarOrdenes(status, from, to, pageable);
    }

    /** GET /api/admin/orders/{id} → 200 OrdenResponse (cualquier pedido; 404 si no existe). */
    @GetMapping("/{id}")
    public OrdenResponse obtenerOrden(@PathVariable Long id) {
        return ordenService.obtenerOrden(id);
    }

    /** PUT /api/admin/orders/{id}/status → 200 (transición validada; 422 si es ilegal; cancelar repone stock). */
    @PutMapping("/{id}/status")
    public OrdenResponse actualizarEstado(@PathVariable Long id,
                                          @Valid @RequestBody OrderStatusUpdateRequest request) {
        return ordenService.actualizarEstado(id, request.status());
    }

    /** GET /api/admin/orders/{id}/comprobante → 200 application/pdf (cualquier pedido pagado). */
    @GetMapping(value = "/{id}/comprobante", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> comprobante(@PathVariable Long id) {
        byte[] pdf = ordenService.comprobantePdf(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(
                ContentDisposition.attachment().filename("comprobante_" + id + ".pdf").build());
        headers.setContentLength(pdf.length);
        return ResponseEntity.ok().headers(headers).body(pdf);
    }
}
