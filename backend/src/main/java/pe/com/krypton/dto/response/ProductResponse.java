package pe.com.krypton.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;

public record ProductResponse(
        Long id,
        String sku,
        String name,
        String description,
        BigDecimal price,
        int stock,
        String imageUrl,
        boolean active,
        Long categoryId,
        String categoryName,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<ProductImageResponse> images) {
}
