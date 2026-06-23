package pe.com.krypton.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/** Seam for binary file storage. Implementations swap local disk for object storage. */
public interface StorageService {

    /**
     * Stores the given file and returns the generated filename (UUID + extension).
     * The caller never supplies a filename — extension is derived from the validated MIME type.
     */
    String store(MultipartFile file);

    /**
     * Loads the file with the given filename as a Spring Resource.
     *
     * @throws pe.com.krypton.exception.StorageException if the file is missing or unreadable
     */
    Resource load(String filename);

    /**
     * Deletes the file with the given filename. No-op if the file does not exist.
     */
    void delete(String filename);
}
