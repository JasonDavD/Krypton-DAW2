package pe.com.krypton.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.krypton.entity.Promo;

public interface PromoRepository extends JpaRepository<Promo, Long> {

    Optional<Promo> findByCode(String code);

    boolean existsByCode(String code);
}
