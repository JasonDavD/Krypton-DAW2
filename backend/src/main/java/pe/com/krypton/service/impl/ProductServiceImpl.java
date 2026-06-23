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
import pe.com.krypton.mapper.ProductMapper;
import pe.com.krypton.model.Category;
import pe.com.krypton.model.Product;
import pe.com.krypton.repository.CategoryRepository;
import pe.com.krypton.repository.ProductRepository;
import pe.com.krypton.service.ProductService;
import pe.com.krypton.spec.ProductSpecification;

@Service
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;

    public ProductServiceImpl(ProductRepository productRepository,
                               CategoryRepository categoryRepository,
                               ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.productMapper = productMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(String name, Long categoryId,
                                                 BigDecimal priceMin, BigDecimal priceMax,
                                                 Pageable pageable) {
        Specification<Product> spec = Specification
                .where(ProductSpecification.isActive(true))
                .and(ProductSpecification.nameLike(name))
                .and(ProductSpecification.hasCategory(categoryId))
                .and(ProductSpecification.priceBetween(priceMin, priceMax));

        Page<ProductResponse> page = productRepository
                .findAll(spec, pageable)
                .map(productMapper::toResponse);

        return PageResponse.of(page);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        Product product = findOrThrow(id);
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
        Category category = findCategoryOrThrow(request.categoryId());

        Product product = new Product();
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
        Product product = findOrThrow(id);

        if (productRepository.existsBySkuAndIdNot(request.sku(), id)) {
            throw new DuplicateSkuException("El SKU ya está registrado en otro producto: " + request.sku());
        }
        Category category = findCategoryOrThrow(request.categoryId());

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
        Product product = findOrThrow(id);
        // SOFT delete: marca como inactivo, no elimina la fila
        product.setActive(false);
        productRepository.save(product);
    }

    // ─── private helpers ────────────────────────────────────────────────────────

    private Product findOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado: " + id));
    }

    private Category findCategoryOrThrow(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada: " + categoryId));
    }
}
