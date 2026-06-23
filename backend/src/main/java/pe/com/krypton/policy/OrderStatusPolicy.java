package pe.com.krypton.policy;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import pe.com.krypton.exception.OrderStatusTransitionException;
import pe.com.krypton.model.enums.OrderStatus;

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
 * (la reposición de stock al cancelar la orquesta OrderServiceImpl, no esta clase).
 */
@Component
public class OrderStatusPolicy {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
            OrderStatus.PENDIENTE,  EnumSet.of(OrderStatus.CONFIRMADA, OrderStatus.CANCELADA),
            OrderStatus.CONFIRMADA, EnumSet.of(OrderStatus.ENVIADO, OrderStatus.CANCELADA),
            OrderStatus.ENVIADO,    EnumSet.of(OrderStatus.ENTREGADO),
            OrderStatus.ENTREGADO,  EnumSet.noneOf(OrderStatus.class),
            OrderStatus.CANCELADA,  EnumSet.noneOf(OrderStatus.class));

    /** Lanza {@link OrderStatusTransitionException} (422) si la transición no es legal. */
    public void assertCanTransition(OrderStatus from, OrderStatus to) {
        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            throw new OrderStatusTransitionException(
                    "Transición de estado inválida: " + from + " → " + to);
        }
    }
}
