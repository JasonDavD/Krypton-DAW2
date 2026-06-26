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
    PageResponse<ProductoResponse> buscar(String name, Long categoryId,
                                         BigDecimal priceMin, BigDecimal priceMax,
                                         Pageable pageable);

    /** Retorna el producto activo o lanza ResourceNotFoundException (404). */
    ProductoResponse buscarPorId(Long id);

    /** Crea un producto. SKU único; categoría debe existir; stock = bootstrap only. */
    ProductoResponse registrar(ProductoRequest request);

    /**
     * Actualiza un producto. SKU único excluyendo propio id.
     * IMPORTANTE: el campo stock del request es ignorado — stock es READ-ONLY post-creación.
     */
    ProductoResponse actualizar(Long id, ProductoRequest request);

    /** Soft-delete: establece active=false. NO elimina la fila. */
    void eliminar(Long id);
}
