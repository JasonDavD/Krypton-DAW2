package pe.com.krypton.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import pe.com.krypton.exception.StorageException;
import pe.com.krypton.service.impl.LocalStorageServiceImpl;

/**
 * Unit test de LocalStorageServiceImpl. Sin Spring, sin DB.
 * La carpeta de uploads es un @TempDir efímero per-test.
 * Strict TDD: RED → GREEN → REFACTOR.
 */
@ExtendWith(MockitoExtension.class)
class LocalStorageServiceImplTest {

    @TempDir
    Path uploadsDir;

    @Mock
    MultipartFile mockFile;

    LocalStorageServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LocalStorageServiceImpl(uploadsDir.toAbsolutePath().toString());
    }

    // ─── store ───────────────────────────────────────────────────────────────────

    @Test
    void store_returns_filename_with_uuid_and_correct_extension() throws IOException {
        when(mockFile.getContentType()).thenReturn("image/jpeg");
        when(mockFile.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));

        String filename = service.store(mockFile);

        assertThat(filename).endsWith(".jpg");
        assertThat(filename).hasSize(40); // 36 UUID chars + 1 dot + 3 ext chars
    }

    @Test
    void store_png_gets_png_extension() throws IOException {
        when(mockFile.getContentType()).thenReturn("image/png");
        when(mockFile.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[]{1}));

        String filename = service.store(mockFile);

        assertThat(filename).endsWith(".png");
    }

    @Test
    void store_webp_gets_webp_extension() throws IOException {
        when(mockFile.getContentType()).thenReturn("image/webp");
        when(mockFile.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[]{1}));

        String filename = service.store(mockFile);

        assertThat(filename).endsWith(".webp");
    }

    // ─── resolveSafe — path traversal guard ──────────────────────────────────────

    @Test
    void resolveSafe_accepts_valid_filename() {
        assertThatCode(() -> service.resolveSafe("abc123.jpg"))
                .doesNotThrowAnyException();
    }

    @Test
    void resolveSafe_rejects_parent_traversal_with_double_dot() {
        assertThatThrownBy(() -> service.resolveSafe("../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nombre de archivo inválido");
    }

    @Test
    void resolveSafe_rejects_sibling_traversal() {
        assertThatThrownBy(() -> service.resolveSafe("../sibling/secret.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nombre de archivo inválido");
    }

    // ─── load ────────────────────────────────────────────────────────────────────

    @Test
    void load_returns_resource_for_existing_file() throws IOException {
        Path file = uploadsDir.resolve("test-image.jpg");
        Files.write(file, new byte[]{0x01, 0x02, 0x03});

        Resource resource = service.load("test-image.jpg");

        assertThat(resource.exists()).isTrue();
        assertThat(resource.isReadable()).isTrue();
    }

    @Test
    void load_throws_StorageException_for_missing_file() {
        assertThatThrownBy(() -> service.load("non-existent.jpg"))
                .isInstanceOf(StorageException.class);
    }

    // ─── delete ──────────────────────────────────────────────────────────────────

    @Test
    void delete_removes_existing_file() throws IOException {
        Path file = uploadsDir.resolve("to-delete.jpg");
        Files.write(file, new byte[]{0x01});

        service.delete("to-delete.jpg");

        assertThat(Files.exists(file)).isFalse();
    }

    @Test
    void delete_does_not_throw_for_non_existent_file() {
        assertThatCode(() -> service.delete("does-not-exist.jpg"))
                .doesNotThrowAnyException();
    }
}
