package pe.com.krypton.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.com.krypton.client.CatalogClient;
import pe.com.krypton.client.dto.ProductoResponse;
import pe.com.krypton.dto.request.CreateResenaRequest;
import pe.com.krypton.entity.Resena;
import pe.com.krypton.exception.DuplicateReviewException;
import pe.com.krypton.mapper.ResenaMapper;
import pe.com.krypton.repository.ResenaRepository;

/**
 * Unit test de la logica de creacion de resenas (crear). Mockea el repo, el cliente Feign
 * (CatalogClient) y el mapper: NO toca DB ni red. Cubre los 2 comportamientos clave:
 *   1) duplicado: el usuario ya reseno -> DuplicateReviewException, NO se guarda,
 *   2) happy path: producto valido y sin resena previa -> se guarda una vez.
 */
@ExtendWith(MockitoExtension.class)
class ResenaServiceImplTest {

    @Mock private ResenaRepository resenaRepository;
    @Mock private CatalogClient catalogClient;
    @Mock private ResenaMapper resenaMapper;

    @InjectMocks private ResenaServiceImpl resenaService;

    private static final String EMAIL = "cliente@krypton.pe";

    private static CreateResenaRequest request() {
        return new CreateResenaRequest(1L, 5, "Excelente producto");
    }

    @Test
    void should_throw_duplicate_when_user_already_reviewed_product() {
        when(catalogClient.getProduct(1L)).thenReturn(new ProductoResponse(1L, "Producto 1", true));
        when(resenaRepository.existsByUserEmailAndProductId(EMAIL, 1L)).thenReturn(true);

        assertThatThrownBy(() -> resenaService.crear(EMAIL, request()))
                .isInstanceOf(DuplicateReviewException.class);

        // ya existia -> NO se persiste nada
        verify(resenaRepository, never()).save(any());
    }

    @Test
    void should_save_review_when_valid() {
        when(catalogClient.getProduct(1L)).thenReturn(new ProductoResponse(1L, "Producto 1", true));
        when(resenaRepository.existsByUserEmailAndProductId(EMAIL, 1L)).thenReturn(false);
        when(resenaRepository.save(any(Resena.class))).thenAnswer(inv -> inv.getArgument(0));

        resenaService.crear(EMAIL, request());

        // producto valido y sin resena previa -> se guarda una sola vez
        verify(resenaRepository, times(1)).save(any(Resena.class));
    }
}
