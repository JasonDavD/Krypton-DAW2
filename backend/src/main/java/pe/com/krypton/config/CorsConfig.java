package pe.com.krypton.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Política CORS para el dev server Vite/React (http://localhost:5173).
 *
 * Usa CorsConfigurationSource bean + http.cors(Customizer.withDefaults()) en
 * SecurityConfig — el único patrón correcto cuando Spring Security está presente.
 * WebMvcConfigurer.addCorsMappings no aplica: el filtro de Security evalúa el
 * preflight OPTIONS ANTES de que llegue a la capa MVC, por lo que ese enfoque
 * produce rechazos 403 inconsistentes.
 *
 * allowedOrigins es lista explícita (no wildcard) — requerido porque
 * allowCredentials=true es incompatible con "*".
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setExposedHeaders(List.of("Content-Disposition"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
