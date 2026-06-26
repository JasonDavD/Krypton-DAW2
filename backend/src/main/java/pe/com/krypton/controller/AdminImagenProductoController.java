package pe.com.krypton.controller;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pe.com.krypton.service.ImagenProductoService;

/**
 * Admin endpoints for managing a product's image gallery.
 * Authorization is inherited from /api/admin/** → hasRole(ADMIN) in SecurityConfig.
 * Controller delegates all logic to ImagenProductoService — never touches repositories.
 */
@RestController
@RequestMapping("/api/admin/products/{productId}/images")
public class AdminImagenProductoController {

    private final ImagenProductoService productImageService;

    public AdminImagenProductoController(ImagenProductoService productImageService) {
        this.productImageService = productImageService;
    }

    /** Upload a new image for the product. */
    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public void subir(@PathVariable Long productId,
                       @RequestParam("file") MultipartFile file) {
        productImageService.subir(productId, file);
    }

    /** Delete an image by its ID. */
    @DeleteMapping("/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long productId,
                       @PathVariable Long imageId) {
        productImageService.eliminar(productId, imageId);
    }

    /**
     * Reorder the product's images. The request body must contain every image ID
     * for the product (STRICT + COMPLETE — D1). Partial or foreign IDs → 400.
     */
    @PatchMapping("/reorder")
    public void reordenar(@PathVariable Long productId,
                        @RequestBody List<Long> orderedIds) {
        productImageService.reordenar(productId, orderedIds);
    }

    /** Set an image as the cover (promoted image syncs product.imageUrl). Idempotent. */
    @PatchMapping("/{imageId}/cover")
    public void definirPortada(@PathVariable Long productId,
                         @PathVariable Long imageId) {
        productImageService.definirPortada(productId, imageId);
    }
}
