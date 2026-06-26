package pe.com.krypton.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Reenvia el header Authorization (Bearer token) del request HTTP entrante a CADA llamada Feign.
 *
 * <p>Sin esto, las llamadas a los endpoints internos de catalog (/api/internal/**, que exigen
 * autenticacion) saldrian SIN token y catalog devolveria 401. Como Feign corre en el mismo hilo
 * del request, recuperamos el request actual via RequestContextHolder y copiamos su token.
 */
@Component
public class FeignAuthInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            String authorization = attrs.getRequest().getHeader("Authorization");
            if (authorization != null && !authorization.isBlank()) {
                template.header("Authorization", authorization);
            }
        }
    }
}
