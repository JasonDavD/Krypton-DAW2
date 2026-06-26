package pe.com.krypton.policy;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import pe.com.krypton.entity.enums.EstadoOrden;
import pe.com.krypton.exception.OrderStatusTransitionException;

/** Unit test de la máquina de estados de la orden. */
class EstadoOrdenPolicyTest {

    private final EstadoOrdenPolicy policy = new EstadoOrdenPolicy();

    @Test
    void should_allow_pendiente_to_confirmada() {
        assertThatCode(() -> policy.assertCanTransition(EstadoOrden.PENDIENTE, EstadoOrden.CONFIRMADA))
                .doesNotThrowAnyException();
    }

    @Test
    void should_reject_confirmada_to_confirmada() {
        assertThatThrownBy(() -> policy.assertCanTransition(EstadoOrden.CONFIRMADA, EstadoOrden.CONFIRMADA))
                .isInstanceOf(OrderStatusTransitionException.class);
    }

    @Test
    void should_reject_transition_from_terminal_state() {
        assertThatThrownBy(() -> policy.assertCanTransition(EstadoOrden.ENTREGADO, EstadoOrden.CONFIRMADA))
                .isInstanceOf(OrderStatusTransitionException.class);
    }
}
