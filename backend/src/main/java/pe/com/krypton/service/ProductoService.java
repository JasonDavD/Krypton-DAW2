package pe.com.krypton.service;

import java.math.BigDecimal;
import org.springframework.data.domain.Pageable;
import pe.com.krypton.dto.request.ProductoRequest;
import pe.com.krypton.dto.response.PageResponse;
import pe.com.krypton.dto.response.ProductoResponse;

/** Operaciones de catálogo para productos. */
public interface ProductoService {

    /**
     * Búsqueda pública con filtros opcionales. Siempre filtra active=true
     * en la ruta pública; el admin puede omitir ese filtro si se expone otra firma.
     */
    PageResponse<ProductoResponse> search(String name, Long categoryId,
                                         BigDecimal priceMin, BigDecimal priceMax,
                                         Pageable pageable);

    /** Retorna el producto activo o lanza ResourceNotFoundException (404). */
    ProductoResponse getById(Long id);

    /** Crea un producto. SKU único; categoría debe existir; stock = bootstrap only. */
    ProductoResponse create(ProductoRequest request);

    /**
     * Actualiza un producto. SKU único excluyendo propio id.
     * IMPORTANTE: el campo stock del request es ignorado — stock es READ-ONLY post-creación.
     */
    ProductoResponse update(Long id, ProductoRequest request);

    /** Soft-delete: establece active=false. NO elimina la fila. */
    void delete(Long id);
}
