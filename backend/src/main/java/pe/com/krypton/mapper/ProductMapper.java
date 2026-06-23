package pe.com.krypton.mapper;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pe.com.krypton.dto.response.ProductImageResponse;
import pe.com.krypton.dto.response.ProductResponse;
import pe.com.krypton.model.Product;
import pe.com.krypton.model.ProductImage;

/**
 * Translates the Product entity to its output DTO. Never exposes the entity outside the service.
 *
 * Two mapping modes:
 * - toResponse()           lean (images = null, omitted by @JsonInclude NON_NULL) — for list/search.
 * - toResponseWithImages() full (images populated, ordered by displayOrder ASC, id ASC) — for getById.
 *
 * The constructor receives the base-url so image URLs are fully qualified.
 */
@Component
public class ProductMapper {

    private final String baseUrl;

    public ProductMapper(
            @Value("${app.uploads.base-url:http://localhost:8080}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /** Lean mapping: images field is null (omitted from JSON via @JsonInclude NON_NULL). */
    public ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStock(),
                product.getImageUrl(),
                product.isActive(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                null);
    }

    /**
     * Full mapping: images collection is loaded (caller must be inside @Transactional(readOnly=true)
     * because the collection is LAZY). The list is already ordered by @OrderBy on Product.images.
     */
    public ProductResponse toResponseWithImages(Product product) {
        List<ProductImageResponse> imageResponses = product.getImages().stream()
                .map(this::toImageResponse)
                .toList();

        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStock(),
                product.getImageUrl(),
                product.isActive(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                imageResponses);
    }

    // ─── private helpers ─────────────────────────────────────────────────────────

    private ProductImageResponse toImageResponse(ProductImage image) {
        String url = baseUrl + "/api/uploads/images/" + image.getPath();
        return new ProductImageResponse(
                image.getId(),
                url,
                image.getDisplayOrder(),
                image.isCover());
    }
}
