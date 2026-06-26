package pe.com.krypton.service;

import pe.com.krypton.dto.request.CreateResenaRequest;
import pe.com.krypton.dto.response.ResenaListResponse;
import pe.com.krypton.dto.response.ResenaResponse;

/** Operaciones de resenas de productos. */
public interface ResenaService {

    /**
     * Crea una resena del usuario autenticado. Valida que el producto exista (Feign a catalog)
     * y que el usuario no lo haya reseniado antes (409 si ya existe).
     */
    ResenaResponse crear(String email, CreateResenaRequest request);

    /** Resenas de un producto + agregados (promedio + total). Publico. */
    ResenaListResponse listarPorProducto(Long productId);
}
