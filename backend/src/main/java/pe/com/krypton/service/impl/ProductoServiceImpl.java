package pe.com.krypton.service.impl;

import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.krypton.dto.request.ProductRequest;
import pe.com.krypton.dto.response.PageResponse;
import pe.com.krypton.dto.response.ProductResponse;
import pe.com.krypton.exception.DuplicateSkuException;
import pe.com.krypton.exception.ResourceNotFoundException;
import pe.com.krypton.mapper.ProductoMapper;
import pe.com.krypton.entity.Categoria;
import pe.com.krypton.entity.Producto;
import pe.com.krypton.repository.CategoriaRepository;
import pe.com.krypton.repository.ProductoRepository;
import pe.com.krypton.service.ProductoService;
import pe.com.krypton.spec.ProductoSpecification;

@Service
public class ProductoServiceImpl implements ProductoService {

    private final ProductoRepository productRepository;
    private final CategoriaRepository categoryRepository;
    private final ProductoMapper productMapper;

    public ProductoServiceImpl(ProductoRepository productRepository,
                               CategoriaRepository categoryRepository,
                               ProductoMapper productMapper) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.productMapper = productMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(String name, Long categoryId,
                                                 BigDecimal priceMin, BigDecimal priceMax,
                                                 Pageable pageable) {
        Specification<Producto> spec = Specification
                .where(ProductoSpecification.isActive(true))
                .and(ProductoSpecification.nameLike(name))
                .and(ProductoSpecification.hasCategory(categoryId))
                .and(ProductoSpecification.priceBetween(priceMin, priceMax));

        Page<ProductResponse> page = productRepository
                .findAll(spec, pageable)
                .map(productMapper::toResponse);

        return PageResponse.of(page);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        Producto product = findOrThrow(id);
        if (!product.isActive()) {
            throw new ResourceNotFoundException("Producto no encontrado: " + id);
        }
        // toResponseWithImages() accesses the LAZY images collection — must remain inside @Transactional
        return productMapper.toResponseWithImages(product);
    }

    @Override
    @Transactional
    public ProductResponse create(ProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new DuplicateSkuException("El SKU ya está registrado: " + request.sku());
        }
        Categoria category = findCategoryOrThrow(request.categoryId());

        Producto product = new Producto();
        product.setSku(request.sku());
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        // stock: bootstrap value only — never mutated by catalog operations after this point
        product.setStock(request.stock() != null ? request.stock() : 0);
        product.setImageUrl(request.imageUrl());
        product.setActive(true);
        product.setCategory(category);

        return productMapper.toResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Producto product = findOrThrow(id);

        if (productRepository.existsBySkuAndIdNot(request.sku(), id)) {
            throw new DuplicateSkuException("El SKU ya está registrado en otro producto: " + request.sku());
        }
        Categoria category = findCategoryOrThrow(request.categoryId());

        product.setSku(request.sku());
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        // stock is READ-ONLY after creation — intentionally NOT updated here
        product.setImageUrl(request.imageUrl());
        product.setCategory(category);

        return productMapper.toResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Producto product = findOrThrow(id);
        // SOFT delete: marca como inactivo, no elimina la fila
        product.setActive(false);
        productRepository.save(product);
    }

    // ─── private helpers ────────────────────────────────────────────────────────

    private Producto findOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado: " + id));
    }

    private Categoria findCategoryOrThrow(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada: " + categoryId));
    }
}
