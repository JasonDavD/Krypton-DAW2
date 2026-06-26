package pe.com.krypton.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.com.krypton.dto.request.AplicarPromoRequest;
import pe.com.krypton.dto.request.CreatePromoRequest;
import pe.com.krypton.dto.response.DescuentoResponse;
import pe.com.krypton.entity.Promo;
import pe.com.krypton.entity.enums.DescuentoTipo;
import pe.com.krypton.exception.DuplicatePromoCodeException;
import pe.com.krypton.exception.PromoInvalidaException;
import pe.com.krypton.mapper.PromoMapper;
import pe.com.krypton.repository.PromoRepository;

/**
 * Unit test de la logica de cupones (PromoServiceImpl). Mockea el repo y el mapper:
 * NO toca DB. Cubre el calculo de descuento (PORCENTAJE/MONTO) y las validaciones
 * (codigo inexistente, inactivo, duplicado en el alta).
 */
@ExtendWith(MockitoExtension.class)
class PromoServiceImplTest {

    @Mock private PromoRepository promoRepository;
    @Mock private PromoMapper promoMapper;

    @InjectMocks private PromoServiceImpl promoService;

    private static Promo promo(DescuentoTipo type, String value, boolean active) {
        Promo p = new Promo();
        p.setId(1L);
        p.setCode("DESC10");
        p.setType(type);
        p.setValue(new BigDecimal(value));
        p.setActive(active);
        return p;
    }

    @Test
    void should_apply_percentage_discount() {
        when(promoRepository.findByCode("DESC10"))
                .thenReturn(Optional.of(promo(DescuentoTipo.PORCENTAJE, "10", true)));

        DescuentoResponse res = promoService.aplicar(
                new AplicarPromoRequest("DESC10", new BigDecimal("100")));

        assertThat(res.discount()).isEqualByComparingTo("10.00");
        assertThat(res.finalAmount()).isEqualByComparingTo("90.00");
    }

    @Test
    void should_apply_fixed_discount() {
        when(promoRepository.findByCode("DESC10"))
                .thenReturn(Optional.of(promo(DescuentoTipo.MONTO, "20", true)));

        DescuentoResponse res = promoService.aplicar(
                new AplicarPromoRequest("DESC10", new BigDecimal("100")));

        assertThat(res.discount()).isEqualByComparingTo("20");
        assertThat(res.finalAmount()).isEqualByComparingTo("80");
    }

    @Test
    void should_throw_when_code_not_found() {
        when(promoRepository.findByCode("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> promoService.aplicar(
                new AplicarPromoRequest("NOPE", new BigDecimal("100"))))
                .isInstanceOf(PromoInvalidaException.class);
    }

    @Test
    void should_throw_when_code_inactive() {
        when(promoRepository.findByCode("DESC10"))
                .thenReturn(Optional.of(promo(DescuentoTipo.PORCENTAJE, "10", false)));

        assertThatThrownBy(() -> promoService.aplicar(
                new AplicarPromoRequest("DESC10", new BigDecimal("100"))))
                .isInstanceOf(PromoInvalidaException.class);
    }

    @Test
    void should_throw_duplicate_when_code_exists() {
        when(promoRepository.existsByCode("DESC10")).thenReturn(true);

        assertThatThrownBy(() -> promoService.crear(
                new CreatePromoRequest("DESC10", DescuentoTipo.PORCENTAJE, new BigDecimal("10"))))
                .isInstanceOf(DuplicatePromoCodeException.class);

        // code duplicado -> NO se persiste nada
        verify(promoRepository, never()).save(any());
    }
}
