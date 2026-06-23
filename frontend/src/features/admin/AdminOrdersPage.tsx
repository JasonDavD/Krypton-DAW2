import { useCallback, useEffect, useState } from 'react';
import { Eye } from 'lucide-react';
import { getAllOrders } from './admin-orders.api';
import { AdminOrderDetailModal } from './AdminOrderDetailModal';
import type { OrderResponse, OrderStatus } from '../../models/order';
import './admin.css';

const pen = new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN', minimumFractionDigits: 2 });
const dateFmt = new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeStyle: 'short' });
const STATUS_LABEL: Record<OrderStatus, string> = { PENDIENTE: 'Pendiente', CONFIRMADA: 'Confirmada', ENVIADO: 'Enviado', ENTREGADO: 'Entregado', CANCELADA: 'Cancelada' };
const PAGE_SIZE = 10;

/** Sección de pedidos del panel admin: lista paginada + detalle con cambio de estado. */
export function AdminOrdersPage() {
  const [orders, setOrders] = useState<OrderResponse[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [selected, setSelected] = useState<OrderResponse | null>(null);

  // Filtros: estado + rango de fecha (el backend usa OrderSpecification).
  const [statusFilter, setStatusFilter] = useState<OrderStatus | ''>('');
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');

  const reload = useCallback(() => {
    setLoading(true);
    setError(false);
    // Las fechas se interpretan en la zona local del admin y se mandan como Instant UTC.
    const from = dateFrom ? new Date(`${dateFrom}T00:00:00`).toISOString() : undefined;
    const to = dateTo ? new Date(`${dateTo}T23:59:59.999`).toISOString() : undefined;
    getAllOrders(page, PAGE_SIZE, { status: statusFilter || undefined, from, to })
      .then((res) => { setOrders(res.content); setTotalPages(res.totalPages); setTotal(res.totalElements); })
      .catch(() => setError(true))
      .finally(() => setLoading(false));
  }, [page, statusFilter, dateFrom, dateTo]);

  useEffect(() => { reload(); }, [reload]);
  useEffect(() => { setPage(0); }, [statusFilter, dateFrom, dateTo]);

  // El modal cambió el estado: refleja la fila y el propio modal (selected = order del modal).
  const onUpdated = (updated: OrderResponse) => {
    setOrders((list) => list.map((o) => (o.id === updated.id ? updated : o)));
    setSelected(updated);
  };

  return (
    <div className="adm">
      <header className="adm-head">
        <div>
          <h1>Pedidos</h1>
          <p className="adm-sub">{loading ? 'Cargando…' : `${total} ${total === 1 ? 'pedido' : 'pedidos'}`}</p>
        </div>
      </header>

      <div className="adm-filters">
        <select className="adm-filter-sel" value={statusFilter} onChange={(e) => setStatusFilter(e.target.value as OrderStatus | '')}>
          <option value="">Todos los estados</option>
          <option value="PENDIENTE">Pendiente</option>
          <option value="CONFIRMADA">Confirmada</option>
          <option value="ENVIADO">Enviado</option>
          <option value="ENTREGADO">Entregado</option>
          <option value="CANCELADA">Cancelada</option>
        </select>
        <label className="adm-date">Desde <input type="date" value={dateFrom} onChange={(e) => setDateFrom(e.target.value)} /></label>
        <label className="adm-date">Hasta <input type="date" value={dateTo} onChange={(e) => setDateTo(e.target.value)} /></label>
      </div>

      {error && <p className="adm-alert">No se pudieron cargar los pedidos.</p>}

      <div className="adm-tablewrap">
        <table className="adm-table">
          <thead>
            <tr><th>#</th><th>Fecha</th><th>Cliente</th><th>Comprob.</th><th>Estado</th><th>Total</th><th aria-label="Acciones"></th></tr>
          </thead>
          <tbody>
            {orders.map((o) => (
              <tr key={o.id}>
                <td className="adm-name">#{o.id}</td>
                <td>{dateFmt.format(new Date(o.orderDate))}</td>
                <td>{o.customerName}</td>
                <td>{o.documentType === 'FACTURA' ? 'Factura' : 'Boleta'}</td>
                <td><span className={`adm-ostatus adm-ostatus--${o.status.toLowerCase()}`}>{STATUS_LABEL[o.status]}</span></td>
                <td>{pen.format(o.total)}</td>
                <td className="adm-actions">
                  <button type="button" title="Ver detalle" onClick={() => setSelected(o)}><Eye size={17} /></button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {!loading && orders.length === 0 && <p className="adm-empty">No hay pedidos.</p>}
      </div>

      {totalPages > 1 && (
        <nav className="adm-pages" aria-label="Paginación">
          <button type="button" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Anterior</button>
          <span>Página {page + 1} de {totalPages}</span>
          <button type="button" disabled={page >= totalPages - 1} onClick={() => setPage((p) => p + 1)}>Siguiente</button>
        </nav>
      )}

      {selected && <AdminOrderDetailModal order={selected} onClose={() => setSelected(null)} onUpdated={onUpdated} />}
    </div>
  );
}
