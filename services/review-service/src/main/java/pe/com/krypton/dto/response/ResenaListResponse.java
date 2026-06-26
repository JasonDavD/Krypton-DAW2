package pe.com.krypton.dto.response;

import java.util.List;

/** Listado de resenas de un producto + agregados (promedio de estrellas y total). */
public record ResenaListResponse(
        Double promedio,
        long total,
        List<ResenaResponse> reviews) {
}
