package pe.com.krypton.policy;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import pe.com.krypton.entity.enums.EstadoOrden;
import pe.com.krypton.exception.OrderStatusTransitionException;

/**
 * Máquina de estados de la orden: qué transiciones son válidas. Responsabilidad única
 * (no produce efectos secundarios; solo valida). El pago dispara PENDIENTE → CONFIRMADA.
 *
 * <pre>
 *   PENDIENTE  → CONFIRMADA, CANCELADA
 *   CONFIRMADA → ENVIADO, CANCELADA
 *   ENVIADO    → ENTREGADO
 *   ENTREGADO  → (terminal)
 *   CANCELADA  → (terminal)
 * </pre>
 */
@Component
public class EstadoOrdenPolicy {

    private static final Map<EstadoOrden, Set<EstadoOrden>> ALLOWED = Map.of(
            EstadoOrden.PENDIENTE, EnumSet.of(EstadoOrden.CONFIRMADA, EstadoOrden.CANCELADA),
            EstadoOrden.CONFIRMADA, EnumSet.of(EstadoOrden.ENVIADO, EstadoOrden.CANCELADA),
            EstadoOrden.ENVIADO, EnumSet.of(EstadoOrden.ENTREGADO),
            EstadoOrden.ENTREGADO, EnumSet.noneOf(EstadoOrden.class),
            EstadoOrden.CANCELADA, EnumSet.noneOf(EstadoOrden.class));

    /** Lanza {@link OrderStatusTransitionException} (422) si la transición no está permitida. */
    public void assertCanTransition(EstadoOrden from, EstadoOrden to) {
        if (!ALLOWED.getOrDefault(from, EnumSet.noneOf(EstadoOrden.class)).contains(to)) {
            throw new OrderStatusTransitionException("Transición de estado inválida: " + from + " -> " + to);
        }
    }
}
