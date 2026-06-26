package pe.com.krypton.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import pe.com.krypton.security.JwtAuthenticationFilter;
import pe.com.krypton.security.RestAccessDeniedHandler;
import pe.com.krypton.security.RestAuthEntryPoint;

/**
 * Cadena de seguridad STATELESS para catalog-service.
 *
 * <p>catalog NO tiene la tabla users: valida el JWT por firma + claims (sin lookup a DB).
 * El JwtAuthenticationFilter ya pobló el SecurityContext con email + ROLE_*.
 *
 * <p>No hay PasswordEncoder ni AuthenticationManager (este servicio NO autentica, solo
 * verifica tokens emitidos por users-service). El CORS lo maneja el api-gateway.
 *
 * <p>Reglas:
 * <ul>
 *   <li>GET /api/products/**, /api/categories/**, /api/uploads/** → público</li>
 *   <li>/api/admin/** → hasRole(ADMIN)</li>
 *   <li>resto → autenticado</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final RestAuthEntryPoint authEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter,
                          RestAuthEntryPoint authEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler) {
        this.jwtFilter = jwtFilter;
        this.authEntryPoint = authEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/categories/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/uploads/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
