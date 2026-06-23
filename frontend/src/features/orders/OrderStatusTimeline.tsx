import type { LucideIcon } from 'lucide-react';
import { Check, Clock, CreditCard, PackageCheck, Truck, XCircle } from 'lucide-react';
import type { OrderStatus } from '../../models/order';

/** Pasos del recorrido normal del pedido. CANCELADA se muestra aparte (no es lineal). */
const STEPS: { status: OrderStatus; label: string; Icon: LucideIcon }[] = [
  { status: 'PENDIENTE', label: 'Pendiente', Icon: Clock },
  { status: 'CONFIRMADA', label: 'Pago confirmado', Icon: CreditCard },
  { status: 'ENVIADO', label: 'Enviado', Icon: Truck },
  { status: 'ENTREGADO', label: 'Entregado', Icon: PackageCheck },
];

/** Posición de cada estado en el recorrido (CANCELADA fuera de la línea). */
const STEP_INDEX: Record<OrderStatus, number> = {
  PENDIENTE: 0, CONFIRMADA: 1, ENVIADO: 2, ENTREGADO: 3, CANCELADA: -1,
};

/**
 * Línea de tiempo del estado del pedido: resalta el paso actual y marca como
 * completados los anteriores. Presentacional: solo depende de `status`.
 */
export function OrderStatusTimeline({ status }: { status: OrderStatus }) {
  if (status === 'CANCELADA') {
    return (
      <div className="otl otl--cancelled">
        <span className="otl__cancel-ic"><XCircle size={20} /></span>
        <div className="otl__cancel-txt">
          <strong>Pedido cancelado</strong>
          <span>Este pedido no continúa el proceso de entrega.</span>
        </div>
      </div>
    );
  }

  const current = STEP_INDEX[status];
  return (
    <ol className="otl" aria-label="Estado del pedido">
      {STEPS.map((step, i) => {
        const state = i < current ? 'done' : i === current ? 'current' : 'todo';
        const Icon = step.Icon;
        return (
          <li key={step.status} className={`otl__step otl__step--${state}`}>
            <span className="otl__dot">
              {state === 'done' ? <Check size={15} /> : <Icon size={15} />}
            </span>
            <span className="otl__label">{step.label}</span>
          </li>
        );
      })}
    </ol>
  );
}
