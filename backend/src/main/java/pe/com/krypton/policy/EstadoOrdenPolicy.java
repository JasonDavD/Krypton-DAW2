package pe.com.krypton.policy;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import pe.com.krypton.exception.OrderStatusTransitionException;
import pe.com.krypton.entity.enums.EstadoOrden;

/**
 * Única fuente de verdad de las transiciones de estado de una orden.
 *
 * <pre>
 *   PENDIENTE  → {CONFIRMADA, CANCELADA}
 *   CONFIRMADA → {ENVIADO, CANCELADA}
 *   ENVIADO    → {ENTREGADO}        (ya no se cancela: el stock salió del almacén)
 *   ENTREGADO  → {}                 (estado terminal)
 *   CANCELADA  → {}                 (estado terminal)
 * </pre>
 *
 * Toda transición fuera de esta tabla —incluida la auto-transición— es ilegal y se
 * mapea a 422 vía {@link OrderStatusTransitionException}.
 *
 * Responsabilidad ÚNICA: responder "¿es legal?". No produce efectos secundarios
 * (la reposición de stock al cancelar la orquesta OrdenServiceImpl, no esta clase).
 */
@Component
public class EstadoOrdenPolicy {

    private static final Map<EstadoOrden, Set<EstadoOrden>> ALLOWED = Map.of(
            EstadoOrden.PENDIENTE,  EnumSet.of(EstadoOrden.CONFIRMADA, EstadoOrden.CANCELADA),
            EstadoOrden.CONFIRMADA, EnumSet.of(EstadoOrden.ENVIADO, EstadoOrden.CANCELADA),
            EstadoOrden.ENVIADO,    EnumSet.of(EstadoOrden.ENTREGADO),
            EstadoOrden.ENTREGADO,  EnumSet.noneOf(EstadoOrden.class),
            EstadoOrden.CANCELADA,  EnumSet.noneOf(EstadoOrden.class));

    /** Lanza {@link OrderStatusTransitionException} (422) si la transición no es legal. */
    public void assertCanTransition(EstadoOrden from, EstadoOrden to) {
        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            throw new OrderStatusTransitionException(
                    "Transición de estado inválida: " + from + " → " + to);
        }
    }
}
