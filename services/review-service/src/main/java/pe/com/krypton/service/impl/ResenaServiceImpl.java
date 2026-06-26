package pe.com.krypton.service.impl;

import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.krypton.client.CatalogClient;
import pe.com.krypton.dto.request.CreateResenaRequest;
import pe.com.krypton.dto.response.ResenaListResponse;
import pe.com.krypton.dto.response.ResenaResponse;
import pe.com.krypton.entity.Resena;
import pe.com.krypton.exception.DuplicateReviewException;
import pe.com.krypton.mapper.ResenaMapper;
import pe.com.krypton.repository.ResenaRepository;
import pe.com.krypton.service.ResenaService;

/**
 * Logica de resenas. El producto vive en catalog-service: antes de guardar validamos su
 * existencia via Feign (si catalog responde 404, el GlobalExceptionHandler lo traduce a 404).
 */
@Service
public class ResenaServiceImpl implements ResenaService {

    private final ResenaRepository resenaRepository;
    private final CatalogClient catalogClient;
    private final ResenaMapper resenaMapper;

    public ResenaServiceImpl(ResenaRepository resenaRepository,
                             CatalogClient catalogClient,
                             ResenaMapper resenaMapper) {
        this.resenaRepository = resenaRepository;
        this.catalogClient = catalogClient;
        this.resenaMapper = resenaMapper;
    }

    @Override
    @Transactional
    public ResenaResponse crear(String email, CreateResenaRequest request) {
        // 1) el producto debe existir en catalog (si no, Feign lanza 404 -> handler -> 404)
        catalogClient.getProduct(request.productId());

        // 2) un usuario resena un producto UNA sola vez
        if (resenaRepository.existsByUserEmailAndProductId(email, request.productId())) {
            throw new DuplicateReviewException("Ya reseñaste este producto");
        }

        // 3) crear y persistir
        Resena resena = new Resena();
        resena.setProductId(request.productId());
        resena.setUserEmail(email);
        resena.setRating(request.rating());
        resena.setComment(request.comment());
        resena.setCreatedAt(Instant.now());

        Resena guardada = resenaRepository.save(resena);
        return resenaMapper.toResponse(guardada);
    }

    @Override
    @Transactional(readOnly = true)
    public ResenaListResponse listarPorProducto(Long productId) {
        List<Resena> resenas = resenaRepository.findByProductIdOrderByCreatedAtDesc(productId);
        long total = resenaRepository.countByProductId(productId);

        Double promedioRaw = resenaRepository.promedioByProductId(productId);
        // sin resenas -> promedio 0.0; con resenas -> redondeo a 1 decimal
        Double promedio = promedioRaw == null
                ? 0.0
                : Math.round(promedioRaw * 10.0) / 10.0;

        List<ResenaResponse> reviews = resenas.stream()
                .map(resenaMapper::toResponse)
                .toList();

        return new ResenaListResponse(promedio, total, reviews);
    }
}
