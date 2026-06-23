import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ChevronRight, Lock, Package } from 'lucide-react';
import { useAuth } from '../../auth/AuthContext';
import { getMyOrders } from './orders.api';
import type { OrderResponse, OrderStatus } from '../../models/order';
import './orders.css';

const pen = new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN', minimumFractionDigits: 2 });
const dateFmt = new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeStyle: 'short' });

const STATUS_LABEL: Record<OrderStatus, string> = {
  PENDIENTE: 'Pendiente de pago',
  CONFIRMADA: 'Confirmada',
  ENVIADO: 'Enviado',
  ENTREGADO: 'Entregado',
  CANCELADA: 'Cancelada',
};

/** Lista de pedidos del usuario autenticado (más nuevos primero). */
export function OrdersPage() {
  const { isAuthenticated } = useAuth();
  const [orders, setOrders] = useState<OrderResponse[] | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!isAuthenticated) { setLoading(false); return; }
    getMyOrders()
      .then(setOrders)
      .catch(() => setOrders([]))
      .finally(() => setLoading(false));
  }, [isAuthenticated]);

  if (!isAuthenticated) {
    return (
      <div className="ord ord-gate">
        <span className="ord-gate__ic"><Lock size={30} /></span>
        <h1>Iniciá sesión para ver tus pedidos</h1>
        <Link to="/cuenta/ingresar" className="ord-gate__cta">Iniciar sesión</Link>
      </div>
    );
  }

  if (loading) return <div className="ord"><p className="ord-status">Cargando tus pedidos…</p></div>;

  if (!orders || orders.length === 0) {
    return (
      <div className="ord ord-empty">
        <span className="ord-empty__ic"><Package size={34} /></span>
        <h1>Todavía no tenés pedidos</h1>
        <p>Cuando completes una compra, vas a verla acá.</p>
        <Link to="/catalogo" className="ord-empty__cta">Ir al catálogo</Link>
      </div>
    );
  }

  return (
    <div className="ord">
      <header className="ord-head"><h1>Mis pedidos</h1></header>

      <ul className="ord-list">
        {orders.map((o) => (
          <li key={o.id}>
            <Link to={`/pedidos/${o.id}`} className="ord-card">
              <div className="ord-card__main">
                <span className="ord-card__id">Pedido #{o.id}</span>
                <span className="ord-card__date">{dateFmt.format(new Date(o.orderDate))}</span>
              </div>
              <span className={`ord-badge ord-badge--${o.status.toLowerCase()}`}>{STATUS_LABEL[o.status]}</span>
              <span className="ord-card__doc">{o.documentType === 'FACTURA' ? 'Factura' : 'Boleta'}</span>
              <span className="ord-card__total">{pen.format(o.total)}</span>
              <ChevronRight size={18} className="ord-card__chev" />
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
