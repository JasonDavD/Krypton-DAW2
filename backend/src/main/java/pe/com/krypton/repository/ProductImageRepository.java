package pe.com.krypton.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.krypton.model.ProductImage;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    long countByProductId(Long productId);

    List<ProductImage> findByProductId(Long productId);

    Optional<ProductImage> findByProductIdAndIsCoverTrue(Long productId);

    /**
     * Promotion candidate after cover deletion: first non-deleted image by
     * lowest display_order (then id as tiebreaker), excluding the image being deleted.
     */
    Optional<ProductImage> findFirstByProductIdAndIdNotOrderByDisplayOrderAscIdAsc(
            Long productId, Long excludedId);
}
