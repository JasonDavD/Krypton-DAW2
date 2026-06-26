package pe.com.krypton.mapper;

import org.springframework.stereotype.Component;
import pe.com.krypton.dto.response.PromoResponse;
import pe.com.krypton.entity.Promo;

/** Mapper manual Entity -> DTO (nunca exponemos la @Entity en la API). */
@Component
public class PromoMapper {

    public PromoResponse toResponse(Promo promo) {
        return new PromoResponse(
                promo.getId(),
                promo.getCode(),
                promo.getType().name(),
                promo.getValue(),
                promo.isActive());
    }
}
