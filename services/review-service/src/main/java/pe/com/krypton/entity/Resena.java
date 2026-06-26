package pe.com.krypton.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Resena de un producto: estrellas (rating 1..5) + comentario opcional.
 * product_id (catalog) y user_email (users) son FK LOGICAS -> otros servicios.
 * UNIQUE (user_email, product_id) en la tabla: un usuario resena un producto UNA vez.
 */
@Entity
@Table(name = "reviews")
@Getter
@Setter
@NoArgsConstructor
public class Resena {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "user_email", nullable = false, length = 160)
    private String userEmail;

    @Column(nullable = false)
    private int rating;

    @Column(length = 1000)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
