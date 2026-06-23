package pe.com.krypton.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import pe.com.krypton.exception.ResourceNotFoundException;
import pe.com.krypton.model.Product;
import pe.com.krypton.model.ProductImage;
import pe.com.krypton.repository.ProductImageRepository;
import pe.com.krypton.repository.ProductRepository;
import pe.com.krypton.service.impl.ProductImageServiceImpl;

/**
 * Unit tests for ProductImageServiceImpl.
 * StorageService and repos are MOCKED — no Spring context, no DB.
 * ArgumentCaptor verifies cover-sync on Product and isCover on ProductImage.
 * Strict TDD: RED → GREEN → REFACTOR.
 */
@ExtendWith(MockitoExtension.class)
class ProductImageServiceImplTest {

    @Mock
    StorageService storageService;
    @Mock
    ProductRepository productRepository;
    @Mock
    ProductImageRepository productImageRepository;
    @Mock
    EntityManager entityManager;

    ProductImageServiceImpl service;

    private static final String BASE_URL = "http://localhost:8080";

    @BeforeEach
    void setUp() {
        service = new ProductImageServiceImpl(storageService, productRepository,
                productImageRepository, entityManager, BASE_URL);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private Product product(Long id) {
        Product p = new Product();
        p.setId(id);
        p.setName("Test Product");
        p.setSku("SKU-" + id);
        return p;
    }

    private ProductImage image(Long id, Long productId, short order, boolean cover) {
        ProductImage img = new ProductImage();
        img.setId(id);
        Product p = product(productId);
        img.setProduct(p);
        img.setPath("uuid" + id + ".jpg");
        img.setDisplayOrder(order);
        img.setCover(cover);
        return img;
    }

    private MockMultipartFile validJpeg(String fieldName) {
        return new MockMultipartFile(fieldName, "photo.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }

    private MockMultipartFile validPng(String fieldName) {
        return new MockMultipartFile(fieldName, "photo.png", "image/png", new byte[]{1, 2, 3});
    }

    // ─── upload: validation ───────────────────────────────────────────────────────

    @Test
    void upload_throws_IllegalArgumentException_for_invalid_content_type() {
        MockMultipartFile gif = new MockMultipartFile("file", "a.gif", "image/gif", new byte[]{1});

        assertThatThrownBy(() -> service.upload(1L, gif))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tipo de archivo no permitido");

        verify(storageService, never()).store(any());
    }

    @Test
    void upload_throws_IllegalArgumentException_when_file_exceeds_5MB() {
        byte[] bigFile = new byte[5 * 1024 * 1024 + 1];
        MockMultipartFile huge = new MockMultipartFile("file", "big.jpg", "image/jpeg", bigFile);

        assertThatThrownBy(() -> service.upload(1L, huge))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5 MB");

        verify(storageService, never()).store(any());
    }

    @Test
    void upload_throws_ResourceNotFoundException_when_product_not_found() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upload(99L, validJpeg("file")))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(storageService, never()).store(any());
    }

    @Test
    void upload_throws_IllegalArgumentException_when_max_images_reached() {
        Product p = product(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        when(productImageRepository.countByProductId(1L)).thenReturn(10L); // already at max

        assertThatThrownBy(() -> service.upload(1L, validJpeg("file")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10");

        verify(storageService, never()).store(any());
    }

    // ─── upload: cover logic on first image ──────────────────────────────────────

    @Test
    void upload_first_image_becomes_cover_and_sets_product_image_url() {
        Product p = product(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        when(productImageRepository.countByProductId(1L)).thenReturn(0L);
        when(storageService.store(any())).thenReturn("uuid1.jpg");
        when(productImageRepository.save(any(ProductImage.class))).thenAnswer(inv -> {
            ProductImage img = inv.getArgument(0);
            img.setId(10L);
            return img;
        });
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        service.upload(1L, validJpeg("file"));

        // Verify ProductImage saved with isCover=true
        ArgumentCaptor<ProductImage> imgCaptor = ArgumentCaptor.forClass(ProductImage.class);
        verify(productImageRepository).save(imgCaptor.capture());
        assertThat(imgCaptor.getValue().isCover()).isTrue();

        // Verify Product.imageUrl synced
        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        assertThat(productCaptor.getValue().getImageUrl())
                .startsWith(BASE_URL)
                .contains("uuid1.jpg");
    }

    @Test
    void upload_second_image_is_not_cover_and_does_not_update_image_url() {
        Product p = product(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        when(productImageRepository.countByProductId(1L)).thenReturn(1L); // already has one
        when(storageService.store(any())).thenReturn("uuid2.jpg");
        when(productImageRepository.save(any(ProductImage.class))).thenAnswer(inv -> {
            ProductImage img = inv.getArgument(0);
            img.setId(11L);
            return img;
        });

        service.upload(1L, validPng("file"));

        ArgumentCaptor<ProductImage> imgCaptor = ArgumentCaptor.forClass(ProductImage.class);
        verify(productImageRepository).save(imgCaptor.capture());
        assertThat(imgCaptor.getValue().isCover()).isFalse();

        // Product.save must NOT be called (image_url not updated)
        verify(productRepository, never()).save(any());
    }

    // ─── delete: non-cover image ──────────────────────────────────────────────────

    @Test
    void delete_non_cover_image_removes_file_and_row_without_touching_product() {
        Product p = product(1L);
        ProductImage img = image(10L, 1L, (short) 0, false); // not cover
        img.setProduct(p);

        when(productImageRepository.findById(10L)).thenReturn(Optional.of(img));
        when(productImageRepository.countByProductId(1L)).thenReturn(2L); // others exist

        service.delete(1L, 10L);

        verify(storageService).delete("uuid10.jpg");
        verify(productImageRepository).delete(img);
        verify(productRepository, never()).save(any());
    }

    // ─── delete: cover image with promotion ──────────────────────────────────────

    @Test
    void delete_cover_image_promotes_next_and_syncs_image_url() {
        Product p = product(1L);
        ProductImage cover = image(10L, 1L, (short) 0, true);
        cover.setProduct(p);
        ProductImage candidate = image(11L, 1L, (short) 1, false);
        candidate.setProduct(p);

        when(productImageRepository.findById(10L)).thenReturn(Optional.of(cover));
        when(productImageRepository.countByProductId(1L)).thenReturn(2L);
        when(productImageRepository
                .findFirstByProductIdAndIdNotOrderByDisplayOrderAscIdAsc(1L, 10L))
                .thenReturn(Optional.of(candidate));
        when(productImageRepository.save(any(ProductImage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        service.delete(1L, 10L);

        // 2 saves: first demotes cover (isCover=false), second promotes candidate (isCover=true)
        ArgumentCaptor<ProductImage> imgCaptor = ArgumentCaptor.forClass(ProductImage.class);
        verify(productImageRepository, times(2)).save(imgCaptor.capture());
        List<ProductImage> savedImages = imgCaptor.getAllValues();
        // first save: the cover demoted
        ProductImage demoted = savedImages.get(0);
        assertThat(demoted.getId()).isEqualTo(10L);
        assertThat(demoted.isCover()).isFalse();
        // second save: the candidate promoted
        ProductImage promoted = savedImages.get(1);
        assertThat(promoted.getId()).isEqualTo(11L);
        assertThat(promoted.isCover()).isTrue();

        // product image_url synced to candidate path
        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        assertThat(productCaptor.getValue().getImageUrl()).contains("uuid11.jpg");

        verify(storageService).delete("uuid10.jpg");
        verify(productImageRepository).delete(cover);
    }

    // ─── delete: last image → imageUrl = null ────────────────────────────────────

    @Test
    void delete_last_image_sets_product_image_url_to_null() {
        Product p = product(1L);
        p.setImageUrl(BASE_URL + "/api/uploads/images/uuid10.jpg");
        ProductImage last = image(10L, 1L, (short) 0, true); // cover AND last
        last.setProduct(p);

        when(productImageRepository.findById(10L)).thenReturn(Optional.of(last));
        when(productImageRepository.countByProductId(1L)).thenReturn(1L); // only one
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        service.delete(1L, 10L);

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        assertThat(productCaptor.getValue().getImageUrl()).isNull();

        verify(storageService).delete("uuid10.jpg");
        verify(productImageRepository).delete(last);
        // No promotion attempted when count == 1
        verify(productImageRepository, never()).findFirstByProductIdAndIdNotOrderByDisplayOrderAscIdAsc(anyLong(), anyLong());
    }

    // ─── reorder: strict + complete ──────────────────────────────────────────────

    @Test
    void reorder_throws_IllegalArgumentException_for_foreign_id_in_body() {
        Product p = product(1L);
        ProductImage img1 = image(10L, 1L, (short) 0, true);
        img1.setProduct(p);
        ProductImage img2 = image(11L, 1L, (short) 1, false);
        img2.setProduct(p);

        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        when(productImageRepository.findByProductId(1L)).thenReturn(List.of(img1, img2));

        // body contains ID 99 which doesn't belong to product 1
        List<Long> foreignIds = List.of(10L, 99L);

        assertThatThrownBy(() -> service.reorder(1L, foreignIds))
                .isInstanceOf(IllegalArgumentException.class);

        verify(productImageRepository, never()).save(any());
    }

    @Test
    void reorder_throws_IllegalArgumentException_for_partial_id_set() {
        Product p = product(1L);
        ProductImage img1 = image(10L, 1L, (short) 0, true);
        img1.setProduct(p);
        ProductImage img2 = image(11L, 1L, (short) 1, false);
        img2.setProduct(p);

        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        when(productImageRepository.findByProductId(1L)).thenReturn(List.of(img1, img2));

        // Only 1 ID given but product has 2 images → partial set
        List<Long> partialIds = List.of(10L);

        assertThatThrownBy(() -> service.reorder(1L, partialIds))
                .isInstanceOf(IllegalArgumentException.class);

        verify(productImageRepository, never()).save(any());
    }

    @Test
    void reorder_valid_full_set_updates_display_order() {
        Product p = product(1L);
        ProductImage img1 = image(10L, 1L, (short) 0, true);
        img1.setProduct(p);
        ProductImage img2 = image(11L, 1L, (short) 1, false);
        img2.setProduct(p);

        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        when(productImageRepository.findByProductId(1L)).thenReturn(List.of(img1, img2));
        when(productImageRepository.save(any(ProductImage.class))).thenAnswer(inv -> inv.getArgument(0));

        // Reverse the order
        service.reorder(1L, List.of(11L, 10L));

        // img2 (11) should now be displayOrder=0, img1 (10) should be displayOrder=1
        ArgumentCaptor<ProductImage> captor = ArgumentCaptor.forClass(ProductImage.class);
        verify(productImageRepository, times(2)).save(captor.capture());
        List<ProductImage> saved = captor.getAllValues();
        ProductImage savedImg2 = saved.stream().filter(i -> i.getId().equals(11L)).findFirst().orElseThrow();
        ProductImage savedImg1 = saved.stream().filter(i -> i.getId().equals(10L)).findFirst().orElseThrow();
        assertThat(savedImg2.getDisplayOrder()).isEqualTo((short) 0);
        assertThat(savedImg1.getDisplayOrder()).isEqualTo((short) 1);
    }

    // ─── setCover ────────────────────────────────────────────────────────────────

    @Test
    void setCover_demotes_previous_cover_and_promotes_target() {
        Product p = product(1L);
        ProductImage currentCover = image(10L, 1L, (short) 0, true);
        currentCover.setProduct(p);
        ProductImage target = image(11L, 1L, (short) 1, false);
        target.setProduct(p);

        when(productImageRepository.findById(11L)).thenReturn(Optional.of(target));
        when(productImageRepository.findByProductIdAndIsCoverTrue(1L))
                .thenReturn(Optional.of(currentCover));
        when(productImageRepository.save(any(ProductImage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        service.setCover(1L, 11L);

        // currentCover demoted
        ArgumentCaptor<ProductImage> imgCaptor = ArgumentCaptor.forClass(ProductImage.class);
        verify(productImageRepository, times(2)).save(imgCaptor.capture());
        List<ProductImage> saved = imgCaptor.getAllValues();
        ProductImage demoted = saved.stream().filter(i -> i.getId().equals(10L)).findFirst().orElseThrow();
        ProductImage promoted = saved.stream().filter(i -> i.getId().equals(11L)).findFirst().orElseThrow();
        assertThat(demoted.isCover()).isFalse();
        assertThat(promoted.isCover()).isTrue();

        // product imageUrl synced to target
        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        assertThat(productCaptor.getValue().getImageUrl()).contains("uuid11.jpg");
    }

    @Test
    void setCover_is_idempotent_when_target_is_already_cover() {
        Product p = product(1L);
        ProductImage alreadyCover = image(10L, 1L, (short) 0, true);
        alreadyCover.setProduct(p);

        when(productImageRepository.findById(10L)).thenReturn(Optional.of(alreadyCover));
        when(productImageRepository.findByProductIdAndIsCoverTrue(1L))
                .thenReturn(Optional.of(alreadyCover));

        // No exception thrown, no extra saves
        service.setCover(1L, 10L);

        verify(productImageRepository, never()).save(any());
        verify(productRepository, never()).save(any());
    }
}
