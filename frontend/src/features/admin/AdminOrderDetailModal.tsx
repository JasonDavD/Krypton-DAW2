import { useState } from 'react';
import { Check, Download, PackageCheck, Truck, X } from 'lucide-react';
import { updateOrderStatus, downloadAdminComprobante } from './admin-orders.api';
import { apiErrorMessage } from '../../lib/apiError';
import type { OrderResponse, OrderStatus } from '../../models/order';

const pen = new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN', minimumFractionDigits: 2 });
const dateFmt = new Intl.DateTimeFormat('es-PE', { dateStyle: 'long', timeStyle: 'short' });
const STATUS_LABEL: Record<OrderStatus, string> = { PENDIENTE: 'Pendiente', CONFIRMADA: 'Confirmada', ENVIADO: 'Enviado', ENTREGADO: 'Entregado', CANCELADA: 'Cancelada' };

/** Detalle de un pedido (admin) + cambio de estado respetando la máquina de estados. */
export function AdminOrderDetailModal({ order, onClose, onUpdated }: {
  order: OrderResponse;
  onClose: () => void;
  onUpdated: (o: OrderResponse) => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [downloadingComp, setDownloadingComp] = useState(false);

  const change = async (status: OrderStatus) => {
    setBusy(true);
    setError(null);
    try {
      onUpdated(await updateOrderStatus(order.id, status));
    } catch (err) {
      setError(apiErrorMessage(err, 'No se pudo cambiar el estado del pedido.'));
    } finally {
      setBusy(false);
    }
  };

  const isFactura = order.documentType === 'FACTURA';
  const base = order.total - order.igv;
  const isPaid = order.status !== 'PENDIENTE' && order.status !== 'CANCELADA';

  const downloadComprobante = async () => {
    setDownloadingComp(true);
    setError(null);
    try {
      await downloadAdminComprobante(order.id);
    } catch (err) {
      setError(apiErrorMessage(err, 'No se pudo descargar el comprobante.'));
    } finally {
      setDownloadingComp(false);
    }
  };

  return (
    <div className="adm-modal" role="dialog" aria-modal="true" onClick={onClose}>
      <div className="adm-modal__panel" onClick={(e) => e.stopPropagation()}>
        <header className="adm-modal__head">
          <h2>Pedido #{order.id}</h2>
          <button type="button" onClick={onClose} aria-label="Cerrar"><X size={20} /></button>
        </header>

        {error && <p className="adm-alert">{error}</p>}

        <div className="adm-od">
          <div className="adm-od__top">
            <span className="adm-od__date">{dateFmt.format(new Date(order.orderDate))}</span>
            <span className={`adm-ostatus adm-ostatus--${order.status.toLowerCase()}`}>{STATUS_LABEL[order.status]}</span>
          </div>

          <div className="adm-od__sec">
            <h3>{isFactura ? 'Factura' : 'Boleta'}</h3>
            <div className="adm-od__line"><span>{isFactura ? 'Razón social' : 'Nombre'}</span><strong>{order.customerName}</strong></div>
            <div className="adm-od__line"><span>{isFactura ? 'RUC' : 'DNI'}</span><strong>{order.customerDoc}</strong></div>
          </div>

          <div className="adm-od__sec">
            <h3>Productos</h3>
            {order.items.map((it) => (
              <div key={it.id} className="adm-od__line">
                <span>{it.productName} · {it.quantity} × {pen.format(it.unitPrice)}</span>
                <strong>{pen.format(it.subtotal)}</strong>
              </div>
            ))}
          </div>

          <div className="adm-od__sec">
            <div className="adm-od__line"><span>Subtotal</span><span>{pen.format(order.subtotal)}</span></div>
            <div className="adm-od__line"><span>Envío</span><span>{order.shippingCost === 0 ? 'Gratis' : pen.format(order.shippingCost)}</span></div>
            {isFactura
              ? <div className="adm-od__line"><span>Op. gravada / IGV</span><span>{pen.format(base)} / {pen.format(order.igv)}</span></div>
              : <div className="adm-od__line"><span>IGV incluido</span><span>{pen.format(order.igv)}</span></div>}
            <div className="adm-od__total"><span>Total</span><span>{pen.format(order.total)}</span></div>
          </div>

          {/* Comprobante: descargable sólo si el pedido está pagado. */}
          {isPaid && (
            <button type="button" className="adm-od__dl" disabled={downloadingComp} onClick={downloadComprobante}>
              <Download size={16} /> {downloadingComp ? 'Generando…' : `Descargar ${isFactura ? 'factura' : 'boleta'} (PDF)`}
            </button>
          )}

          {/* Acciones según la máquina de estados (sólo transiciones legales). */}
          {order.status === 'PENDIENTE' && (
            <div className="adm-od__actions">
              <button type="button" className="adm-btn" disabled={busy} onClick={() => change('CONFIRMADA')}><Check size={17} /> Confirmar</button>
              <button type="button" className="adm-btn-danger" disabled={busy} onClick={() => change('CANCELADA')}>Cancelar pedido</button>
            </div>
          )}
          {order.status === 'CONFIRMADA' && (
            <div className="adm-od__actions">
              <button type="button" className="adm-btn" disabled={busy} onClick={() => change('ENVIADO')}><Truck size={17} /> Marcar enviado</button>
              <button type="button" className="adm-btn-danger" disabled={busy} onClick={() => change('CANCELADA')}>Cancelar pedido</button>
            </div>
          )}
          {order.status === 'ENVIADO' && (
            <div className="adm-od__actions">
              <button type="button" className="adm-btn" disabled={busy} onClick={() => change('ENTREGADO')}><PackageCheck size={17} /> Marcar entregado</button>
            </div>
          )}
          {order.status === 'ENTREGADO' && <p className="adm-od__final">Pedido entregado — estado final.</p>}
          {order.status === 'CANCELADA' && <p className="adm-od__final">Pedido cancelado — estado final.</p>}
        </div>
      </div>
    </div>
  );
}
