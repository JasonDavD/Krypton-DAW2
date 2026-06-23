package pe.com.krypton.controller;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.com.krypton.exception.StorageException;
import pe.com.krypton.service.StorageService;

/**
 * Public endpoint that streams uploaded images.
 * Security: GET /api/uploads/** is permitAll in SecurityConfig.
 *
 * Path-traversal guard: any slash (/ or \) in the filename segment is rejected with 400
 * BEFORE delegating to StorageService. The storage layer has its own canonicalize guard too.
 * Both guards work together as defence-in-depth (ADR-D2).
 */
@RestController
@RequestMapping("/api/uploads/images")
public class ImageServeController {

    /** 7-day public cache — matches design spec. */
    private static final String CACHE_CONTROL_VALUE = "max-age=604800, public";

    private final StorageService storageService;

    public ImageServeController(StorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping("/{filename}")
    public ResponseEntity<Resource> serve(@PathVariable String filename) {
        // Path-traversal guard: reject any filename that contains a directory separator
        if (filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            throw new IllegalArgumentException(
                    "Nombre de archivo inválido (path traversal detectado): " + filename);
        }

        Resource resource;
        try {
            resource = storageService.load(filename);
        } catch (StorageException ex) {
            // StorageException means file is missing or unreadable → 404
            throw new pe.com.krypton.exception.ResourceNotFoundException(
                    "Imagen no encontrada: " + filename);
        }

        var mediaType = MediaTypeFactory.getMediaType(resource)
                .orElse(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM);

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_VALUE)
                .contentType(mediaType)
                .body(resource);
    }
}
