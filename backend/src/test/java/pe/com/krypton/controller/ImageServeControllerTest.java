package pe.com.krypton.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.web.servlet.MockMvc;
import pe.com.krypton.exception.StorageException;
import pe.com.krypton.security.JwtAuthenticationFilter;
import pe.com.krypton.service.StorageService;

/**
 * Web slice for ImageServeController.
 * Security is disabled via addFilters=false for the slice — permitAll is verified in integration.
 */
@WebMvcTest(controllers = ImageServeController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
class ImageServeControllerTest {

    @Autowired MockMvc mvc;
    @MockBean StorageService storageService;

    @Test
    void should_return_200_with_body_when_image_exists() throws Exception {
        byte[] imageBytes = "fake-image-data".getBytes();
        ByteArrayResource resource = new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return "abc123.jpg";
            }
        };
        when(storageService.load("abc123.jpg")).thenReturn(resource);

        mvc.perform(get("/api/uploads/images/abc123.jpg"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(imageBytes))
                .andExpect(header().string("Cache-Control", "max-age=604800, public"));
    }

    @Test
    void should_return_404_when_image_missing() throws Exception {
        when(storageService.load("missing.jpg"))
                .thenThrow(new StorageException("Archivo no encontrado: missing.jpg"));

        mvc.perform(get("/api/uploads/images/missing.jpg"))
                .andExpect(status().isNotFound());
    }

    @Test
    void should_return_400_when_filename_is_path_traversal() throws Exception {
        // Path-traversal attempt: ../etc/passwd (URL-encoded)
        mvc.perform(get("/api/uploads/images/..%2Fetc%2Fpasswd"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_return_400_when_filename_contains_dotdot() throws Exception {
        // A filename with ".." prefix is a traversal attempt caught by the controller guard
        mvc.perform(get("/api/uploads/images/..secret.jpg"))
                .andExpect(status().isBadRequest());
    }
}
