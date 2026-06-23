package pe.com.krypton.dto.response;

/**
 * Read-only DTO for a product image.
 * url is the full serving URL (baseUrl + /api/uploads/images/{filename}).
 */
public record ProductImageResponse(
        Long id,
        String url,
        short displayOrder,
        boolean cover) {
}
