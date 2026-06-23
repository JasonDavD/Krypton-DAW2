package pe.com.krypton.service.impl;

import jakarta.persistence.EntityManager;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pe.com.krypton.exception.ResourceNotFoundException;
import pe.com.krypton.model.Product;
import pe.com.krypton.model.ProductImage;
import pe.com.krypton.repository.ProductImageRepository;
import pe.com.krypton.repository.ProductRepository;
import pe.com.krypton.service.ProductImageService;
import pe.com.krypton.service.StorageService;

/**
 * Manages the image gallery for a product.
 *
 * Cover algorithm:
 * - upload:     isCover = (existingCount == 0); on cover, sync product.imageUrl
 * - delete:     count first; if count==1 → imageUrl=null; if cover+others → promote lowest order
 * - setCover:   demote current (flush via save) → promote target; sync imageUrl
 * - reorder:    STRICT+COMPLETE — body must match product's exact ID set; update displayOrder
 */
@Service
public class ProductImageServiceImpl implements ProductImageService {

    private static final int MAX_IMAGES_PER_PRODUCT = 10;
    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024; // 5 MB
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final StorageService storageService;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final EntityManager entityManager;
    private final String baseUrl;

    public ProductImageServiceImpl(
            StorageService storageService,
            ProductRepository productRepository,
            ProductImageRepository productImageRepository,
            EntityManager entityManager,
            @Value("${app.uploads.base-url:http://localhost:8080}") String baseUrl) {
        this.storageService = storageService;
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.entityManager = entityManager;
        this.baseUrl = baseUrl;
    }

    // ─── upload ──────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void upload(Long productId, MultipartFile file) {
        // 1. Validate content-type
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException(
                    "Tipo de archivo no permitido: " + file.getContentType()
                    + ". Tipos aceptados: jpeg, png, webp.");
        }
        // 2. Validate size
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "El archivo supera el límite de 5 MB (recibido: " + file.getSize() + " bytes).");
        }
        // 3. Product must exist
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado: " + productId));

        // 4. Max images check
        long count = productImageRepository.countByProductId(productId);
        if (count >= MAX_IMAGES_PER_PRODUCT) {
            throw new IllegalArgumentException(
                    "El producto ya tiene el máximo de " + MAX_IMAGES_PER_PRODUCT + " imágenes.");
        }

        // 5. Store binary (after all validations pass)
        String filename = storageService.store(file);

        // 6. Persist row
        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setPath(filename);
        image.setDisplayOrder((short) count); // next slot
        boolean isFirstImage = (count == 0);
        image.setCover(isFirstImage);
        productImageRepository.save(image);

        // 7. On first image: sync product.imageUrl
        if (isFirstImage) {
            product.setImageUrl(serveUrl(filename));
            productRepository.save(product);
        }
    }

    // ─── delete ──────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void delete(Long productId, Long imageId) {
        ProductImage image = productImageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Imagen no encontrada: " + imageId));

        long totalCount = productImageRepository.countByProductId(productId);

        if (totalCount == 1) {
            // Last image: null out product imageUrl
            Product product = image.getProduct();
            product.setImageUrl(null);
            productRepository.save(product);
        } else if (image.isCover()) {
            // Cover deleted with siblings: promote next by lowest displayOrder.
            // IMPORTANT: demote the cover FIRST and flush before promoting the candidate —
            // the partial unique index (one cover per product) rejects two rows with
            // is_cover=true for the same product_id in the same flush cycle (ADR-D5).
            image.setCover(false);
            productImageRepository.save(image);
            entityManager.flush();

            ProductImage candidate = productImageRepository
                    .findFirstByProductIdAndIdNotOrderByDisplayOrderAscIdAsc(productId, imageId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "No hay candidato para promover como portada."));
            candidate.setCover(true);
            productImageRepository.save(candidate);

            Product product = image.getProduct();
            product.setImageUrl(serveUrl(candidate.getPath()));
            productRepository.save(product);
        }
        // non-cover + others exist: nothing to change on product

        storageService.delete(image.getPath());
        productImageRepository.delete(image);
    }

    // ─── reorder ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void reorder(Long productId, List<Long> orderedIds) {
        // Strict + Complete: ordered IDs must exactly match the product's images
        productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado: " + productId));

        List<ProductImage> existing = productImageRepository.findByProductId(productId);
        Set<Long> existingIds = existing.stream().map(ProductImage::getId).collect(Collectors.toSet());
        Set<Long> requestIds = new HashSet<>(orderedIds);

        if (!existingIds.equals(requestIds)) {
            throw new IllegalArgumentException(
                    "El conjunto de IDs no coincide con las imágenes del producto. "
                    + "Se requieren exactamente los IDs: " + existingIds);
        }

        Map<Long, ProductImage> imageById = existing.stream()
                .collect(Collectors.toMap(ProductImage::getId, Function.identity()));

        for (int i = 0; i < orderedIds.size(); i++) {
            ProductImage img = imageById.get(orderedIds.get(i));
            img.setDisplayOrder((short) i);
            productImageRepository.save(img);
        }
    }

    // ─── setCover ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void setCover(Long productId, Long imageId) {
        ProductImage target = productImageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Imagen no encontrada: " + imageId));

        Optional<ProductImage> currentCoverOpt =
                productImageRepository.findByProductIdAndIsCoverTrue(productId);

        // Idempotent: if target is already cover, no-op
        if (currentCoverOpt.isPresent() && currentCoverOpt.get().getId().equals(imageId)) {
            return;
        }

        // Demote current cover first, then flush explicitly before promoting the target.
        // The partial unique index (one cover per product) rejects two rows with
        // is_cover=true for the same product_id in the same Hibernate flush cycle.
        // Flushing after the demote forces the UPDATE to hit the DB before promoting
        // the new cover — mirroring the same fix applied in delete() (ADR-D5).
        if (currentCoverOpt.isPresent()) {
            ProductImage current = currentCoverOpt.get();
            current.setCover(false);
            productImageRepository.save(current);
            entityManager.flush();
        }

        // Promote target
        target.setCover(true);
        productImageRepository.save(target);

        // Sync product imageUrl
        Product product = target.getProduct();
        product.setImageUrl(serveUrl(target.getPath()));
        productRepository.save(product);
    }

    // ─── private helpers ─────────────────────────────────────────────────────────

    private String serveUrl(String filename) {
        return baseUrl + "/api/uploads/images/" + filename;
    }
}
