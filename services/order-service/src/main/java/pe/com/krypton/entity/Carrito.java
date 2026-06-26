package pe.com.krypton.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Carrito: uno por usuario (user_email UNIQUE). Identidad = email del JWT (sub).
 * ADAPTACION microservicios: NO referencia a Usuario (vive en users-service);
 * el dueño se guarda como email suelto. Los items se cargan por repo (SIN @OneToMany).
 */
@Entity
@Table(name = "cart")
@Getter
@Setter
@NoArgsConstructor
public class Carrito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_email", nullable = false, unique = true)
    private String userEmail;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
