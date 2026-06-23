package pe.com.krypton.service.impl;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import pe.com.krypton.exception.StorageException;
import pe.com.krypton.service.StorageService;

/**
 * Filesystem implementation of StorageService.
 *
 * Key design decisions:
 * - Filename is ALWAYS UUID + extension derived from validated MIME type (never the client filename).
 *   This closes two attack vectors: (1) filename injection, (2) extension spoofing.
 * - resolveSafe() canonicalises the resolved path and asserts it starts with the canonical
 *   upload directory root — path-traversal guard (ADR-D2).
 * - IOException is wrapped in StorageException (maps to HTTP 500 via GlobalExceptionHandler).
 */
@Service
public class LocalStorageServiceImpl implements StorageService {

    private static final Map<String, String> MIME_TO_EXT = Map.of(
            "image/jpeg", "jpg",
            "image/png",  "png",
            "image/webp", "webp"
    );

    private final Path uploadDir;

    public LocalStorageServiceImpl(@Value("${app.uploads.dir:./uploads}") String uploadsDir) {
        this.uploadDir = Paths.get(uploadsDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException ex) {
            throw new StorageException("No se pudo crear el directorio de uploads: " + uploadDir, ex);
        }
    }

    @Override
    public String store(MultipartFile file) {
        String ext = extensionFor(file.getContentType());
        String filename = UUID.randomUUID() + "." + ext;
        Path destination = uploadDir.resolve(filename);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new StorageException("Error al guardar el archivo: " + filename, ex);
        }
        return filename;
    }

    @Override
    public Resource load(String filename) {
        Path path = resolveSafe(filename);
        try {
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new StorageException("Archivo no encontrado o no legible: " + filename);
            }
            return resource;
        } catch (java.net.MalformedURLException ex) {
            throw new StorageException("URL de archivo mal formada: " + filename, ex);
        }
    }

    @Override
    public void delete(String filename) {
        Path path = resolveSafe(filename);
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            throw new StorageException("Error al eliminar el archivo: " + filename, ex);
        }
    }

    /**
     * Resolves {@code filename} relative to {@code uploadDir} and asserts no path traversal.
     * Canonical path must start with the canonical upload directory.
     *
     * @throws IllegalArgumentException if the resolved path escapes the upload directory
     */
    public Path resolveSafe(String filename) {
        Path resolved = uploadDir.resolve(filename).normalize();
        if (!resolved.startsWith(uploadDir)) {
            throw new IllegalArgumentException(
                    "Nombre de archivo inválido (path traversal detectado): " + filename);
        }
        return resolved;
    }

    // ─── private helpers ─────────────────────────────────────────────────────────

    private String extensionFor(String contentType) {
        String ext = MIME_TO_EXT.get(contentType);
        if (ext == null) {
            throw new IllegalArgumentException("Tipo de archivo no permitido: " + contentType);
        }
        return ext;
    }
}
