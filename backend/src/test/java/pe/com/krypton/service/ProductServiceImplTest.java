package pe.com.krypton.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import pe.com.krypton.dto.request.ProductRequest;
import pe.com.krypton.dto.response.PageResponse;
import pe.com.krypton.dto.response.ProductImageResponse;
import pe.com.krypton.dto.response.ProductResponse;
import pe.com.krypton.model.ProductImage;
import pe.com.krypton.exception.DuplicateSkuException;
import pe.com.krypton.exception.ResourceNotFoundException;
import pe.com.krypton.mapper.ProductMapper;
import pe.com.krypton.model.Category;
import pe.com.krypton.model.Product;
import pe.com.krypton.repository.CategoryRepository;
import pe.com.krypton.repository.ProductRepository;
import pe.com.krypton.service.impl.ProductServiceImpl;

/**
 * Unit test de ProductServiceImpl. Repos MOCKEADOS, sin Spring context, sin DB.
 * TDD: RED escrito antes de que exista ProductServiceImpl.
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock ProductRepository productRepository;
    @Mock CategoryRepository categoryRepository;

    ProductServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProductServiceImpl(productRepository, categoryRepository, new ProductMapper("http://localhost:8080"));
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    private Category category(Long id) {
        Category c = new Category();
        c.setId(id);
        c.setName("Electronics");
        c.setDescription("Desc");
        return c;
    }

    private Product product(Long id, String sku, boolean active) {
        Product p = new Product();
        p.setId(id);
        p.setSku(sku);
        p.setName("Product " + id);
        p.setDescription("Desc");
        p.setPrice(new BigDecimal("99.99"));
        p.setStock(10);
        p.setImageUrl(null);
        p.setActive(active);
        p.setCategory(category(1L));
        return p;
    }

    private ProductRequest request(String sku, Long categoryId, Integer stock) {
        return new ProductRequest(sku, "Some Product", "Desc",
                new BigDecimal("49.99"), stock, null, categoryId);
    }

    // ─── search ─────────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void should_return_page_of_active_products_for_public_search() {
        Pageable pageable = PageRequest.of(0, 10);
        Product p = product(1L, "SKU-001", true);
        Page<Product> page = new PageImpl<>(List.of(p), pageable, 1);
        when(productRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        PageResponse<ProductResponse> result = service.search(null, null, null, null, pageable);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content().get(0).sku()).isEqualTo("SKU-001");
    }

    // ─── getById ────────────────────────────────────────────────────────────────

    @Test
    void should_return_product_response_when_product_is_active() {
        Product p = product(1L, "SKU-001", true);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        ProductResponse result = service.getById(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.sku()).isEqualTo("SKU-001");
    }

    @Test
    void getById_should_return_images_ordered_by_displayOrder() {
        Product p = product(1L, "SKU-001", true);

        ProductImage img1 = new ProductImage();
        img1.setId(10L);
        img1.setPath("a.jpg");
        img1.setDisplayOrder((short) 0);
        img1.setCover(true);
        img1.setProduct(p);

        ProductImage img2 = new ProductImage();
        img2.setId(11L);
        img2.setPath("b.jpg");
        img2.setDisplayOrder((short) 1);
        img2.setCover(false);
        img2.setProduct(p);

        p.getImages().add(img1);
        p.getImages().add(img2);

        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        ProductResponse result = service.getById(1L);

        assertThat(result.images()).isNotNull();
        assertThat(result.images()).hasSize(2);
        assertThat(result.images().get(0).id()).isEqualTo(10L);
        assertThat(result.images().get(0).cover()).isTrue();
        assertThat(result.images().get(0).url()).startsWith("http://localhost:8080");
        assertThat(result.images().get(1).id()).isEqualTo(11L);
        assertThat(result.images().get(1).cover()).isFalse();
    }

    @Test
    void search_should_return_products_without_images_field() {
        Pageable pageable = PageRequest.of(0, 10);
        Product p = product(1L, "SKU-001", true);
        Page<Product> page = new PageImpl<>(List.of(p), pageable, 1);
        when(productRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        PageResponse<ProductResponse> result = service.search(null, null, null, null, pageable);

        assertThat(result.content()).hasSize(1);
        // images field must be null (lean mapping) so @JsonInclude(NON_NULL) omits it in JSON
        assertThat(result.content().get(0).images()).isNull();
    }

    @Test
    void should_throw_not_found_when_product_does_not_exist() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void should_throw_not_found_when_product_exists_but_is_inactive() {
        Product p = product(2L, "SKU-002", false);
        when(productRepository.findById(2L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.getById(2L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── create ─────────────────────────────────────────────────────────────────

    @Test
    void should_create_product_when_sku_is_unique_and_category_exists() {
        ProductRequest req = request("NEW-SKU", 1L, 5);
        Category cat = category(1L);
        when(productRepository.existsBySku("NEW-SKU")).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(cat));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(10L);
            return p;
        });

        ProductResponse result = service.create(req);

        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.sku()).isEqualTo("NEW-SKU");
        assertThat(result.stock()).isEqualTo(5); // bootstrap value from request
    }

    @Test
    void should_reject_create_when_sku_already_exists() {
        when(productRepository.existsBySku("DUP-SKU")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("DUP-SKU", 1L, 0)))
                .isInstanceOf(DuplicateSkuException.class);
        verify(productRepository, never()).save(any());
    }

    @Test
    void should_reject_create_when_category_not_found() {
        when(productRepository.existsBySku("SKU-X")).thenReturn(false);
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request("SKU-X", 99L, 0)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(productRepository, never()).save(any());
    }

    // ─── update ─────────────────────────────────────────────────────────────────

    @Test
    void should_update_product_fields_but_never_change_stock() {
        Product existing = product(1L, "OLD-SKU", true);
        existing.setStock(42); // stock must stay 42 after update
        Category cat = category(1L);

        ProductRequest req = new ProductRequest("NEW-SKU", "New Name", "New Desc",
                new BigDecimal("199.99"), 999 /* ignored */, "http://img.png", 1L);

        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepository.existsBySkuAndIdNot("NEW-SKU", 1L)).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(cat));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductResponse result = service.update(1L, req);

        assertThat(result.name()).isEqualTo("New Name");
        assertThat(result.sku()).isEqualTo("NEW-SKU");
        assertThat(result.stock()).isEqualTo(42); // unchanged — stock is READ-ONLY after create

        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(saved.capture());
        assertThat(saved.getValue().getStock()).isEqualTo(42);
    }

    @Test
    void should_reject_update_when_new_sku_belongs_to_another_product() {
        Product existing = product(1L, "OLD-SKU", true);
        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepository.existsBySkuAndIdNot("TAKEN-SKU", 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(1L, request("TAKEN-SKU", 1L, 0)))
                .isInstanceOf(DuplicateSkuException.class);
        verify(productRepository, never()).save(any());
    }

    @Test
    void should_allow_update_keeping_same_sku() {
        Product existing = product(1L, "SAME-SKU", true);
        Category cat = category(1L);

        ProductRequest req = request("SAME-SKU", 1L, 0);
        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepository.existsBySkuAndIdNot("SAME-SKU", 1L)).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(cat));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductResponse result = service.update(1L, req);

        assertThat(result.sku()).isEqualTo("SAME-SKU");
    }

    // ─── delete (soft) ──────────────────────────────────────────────────────────

    @Test
    void should_soft_delete_product_by_setting_active_false() {
        Product existing = product(1L, "SKU-001", true);
        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        service.delete(1L);

        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(saved.capture());
        assertThat(saved.getValue().isActive()).isFalse(); // soft delete = active=false
    }

    @Test
    void should_throw_not_found_on_delete_when_product_missing() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(productRepository, never()).save(any());
    }
}
