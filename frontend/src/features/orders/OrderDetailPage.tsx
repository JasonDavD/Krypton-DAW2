import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Check, CreditCard, Download, Lock } from 'lucide-react';
import { useAuth } from '../../auth/AuthContext';
import { getMyOrder, payOrder, downloadMyComprobante } from './orders.api';
import { OrderStatusTimeline } from './OrderStatusTimeline';
import type { OrderResponse, OrderStatus, PaymentMethod } from '../../models/order';
import './orders.css';
import './order-detail.css';

const pen = new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN', minimumFractionDigits: 2 });
const dateFmt = new Intl.DateTimeFormat('es-PE', { dateStyle: 'long', timeStyle: 'short' });

const STATUS_LABEL: Record<OrderStatus, string> = {
  PENDIENTE: 'Pendiente de pago', CONFIRMADA: 'Confirmada',
  ENVIADO: 'Enviado', ENTREGADO: 'Entregado', CANCELADA: 'Cancelada',
};
const PAY_METHODS: { value: PaymentMethod; label: string }[] = [
  { value: 'YAPE', label: 'Yape' },
  { value: 'CREDIT_CARD', label: 'Tarjeta de crédito' },
  { value: 'DEBIT_CARD', label: 'Tarjeta de débito' },
];

const onlyDigits = (s: string) => s.replace(/\D/g, '');
/** Agrupa de a 4 dígitos. Acepta 13–19 dígitos (Visa/MC 16, Amex 15, Maestro 13–19). */
const formatCardNumber = (s: string) => onlyDigits(s).slice(0, 19).replace(/(.{4})(?=.)/g, '$1 ');
/** MM/AA mientras se escribe. */
const formatExpiry = (s: string) => {
  const d = onlyDigits(s).slice(0, 4);
  return d.length <= 2 ? d : `${d.slice(0, 2)}/${d.slice(2)}`;
};

/** Detalle de un pedido: comprobante, líneas, desglose de montos y pago si está pendiente. */
export function OrderDetailPage() {
  const { id } = useParams();
  const { isAuthenticated } = useAuth();

  const [order, setOrder] = useState<OrderResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [method, setMethod] = useState<PaymentMethod>('YAPE');
  const [paying, setPaying] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Datos del pago SIMULADO (no se envían al backend; sólo se valida el formulario).
  const [cardNumber, setCardNumber] = useState('');
  const [cardExpiry, setCardExpiry] = useState('');
  const [cardCvv, setCardCvv] = useState('');
  const [cardName, setCardName] = useState('');
  const [yapePhone, setYapePhone] = useState('');
  const [yapeCode, setYapeCode] = useState('');

  // Descarga del comprobante.
  const [downloadingComp, setDownloadingComp] = useState(false);
  const [compError, setCompError] = useState<string | null>(null);

  useEffect(() => {
    if (!isAuthenticated) { setLoading(false); return; }
    const orderId = Number(id);
    if (!Number.isFinite(orderId)) { setNotFound(true); setLoading(false); return; }
    getMyOrder(orderId)
      .then(setOrder)
      .catch(() => setNotFound(true))
      .finally(() => setLoading(false));
  }, [id, isAuthenticated]);

  if (!isAuthenticated) {
    return <div className="ord ord-gate"><h1>Iniciá sesión para ver el pedido</h1>
      <Link to="/cuenta/ingresar" className="ord-gate__cta">Iniciar sesión</Link></div>;
  }
  if (loading) return <div className="ord"><p className="ord-status">Cargando pedido…</p></div>;
  if (notFound || !order) {
    return (
      <div className="ord ord-empty">
        <h1>Pedido no encontrado</h1>
        <p>No existe o no te pertenece.</p>
        <Link to="/pedidos" className="ord-empty__cta">Volver a mis pedidos</Link>
      </div>
    );
  }

  const base = order.total - order.igv; // base imponible = total − IGV
  const isFactura = order.documentType === 'FACTURA';
  const isPaid = order.status !== 'PENDIENTE' && order.status !== 'CANCELADA';

  // Validación del formulario de pago simulado (granular, para dar feedback por campo).
  const isCard = method === 'CREDIT_CARD' || method === 'DEBIT_CARD';
  const cardNumberDigits = onlyDigits(cardNumber).length;
  const cardNumberOk = cardNumberDigits >= 13 && cardNumberDigits <= 19;
  const cardExpiryOk = /^(0[1-9]|1[0-2])\/\d{2}$/.test(cardExpiry);
  const cardCvvOk = cardCvv.length >= 3; // 3 (Visa/MC) o 4 (Amex)
  const cardNameOk = cardName.trim().length > 0;
  const cardValid = cardNumberOk && cardExpiryOk && cardCvvOk && cardNameOk;
  const yapeValid = yapePhone.length === 9 && yapeCode.length === 6;
  const payValid = isCard ? cardValid : yapeValid;

  const pay = async () => {
    if (!payValid) return;
    setPaying(true);
    setError(null);
    try {
      // El pago es SIMULADO: sólo se confirma el método; los datos de tarjeta no salen del browser.
      setOrder(await payOrder(order.id, { method }));
    } catch {
      setError('No se pudo procesar el pago. Intentá de nuevo.');
    } finally {
      setPaying(false);
    }
  };

  const downloadComprobante = async () => {
    setDownloadingComp(true);
    setCompError(null);
    try {
      await downloadMyComprobante(order.id);
    } catch {
      setCompError('No se pudo descargar el comprobante.');
    } finally {
      setDownloadingComp(false);
    }
  };

  return (
    <div className="ord od">
      <nav className="od-crumb"><Link to="/pedidos">Mis pedidos</Link><span>/</span><strong>Pedido #{order.id}</strong></nav>

      <header className="od-head">
        <div>
          <h1>Pedido #{order.id}</h1>
          <span className="od-date">{dateFmt.format(new Date(order.orderDate))}</span>
        </div>
        <span className={`ord-badge ord-badge--${order.status.toLowerCase()}`}>{STATUS_LABEL[order.status]}</span>
      </header>

      <OrderStatusTimeline status={order.status} />

      <div className="od-grid">
        <section className="od-main">
          {/* COMPROBANTE */}
          <div className="od-card od-doc">
            <h2>{isFactura ? 'Factura' : 'Boleta'}</h2>
            <div className="od-doc__row"><span>{isFactura ? 'Razón social' : 'Nombre'}</span><strong>{order.customerName}</strong></div>
            <div className="od-doc__row"><span>{isFactura ? 'RUC' : 'DNI'}</span><strong>{order.customerDoc}</strong></div>
          </div>

          {/* LÍNEAS */}
          <div className="od-card">
            <h2>Productos</h2>
            <ul className="od-items">
              {order.items.map((it) => (
                <li key={it.id} className="od-item">
                  <Link to={`/catalogo/${it.productId}`} className="od-item__name">{it.productName}</Link>
                  <span className="od-item__qty">{it.quantity} × {pen.format(it.unitPrice)}</span>
                  <span className="od-item__sub">{pen.format(it.subtotal)}</span>
                </li>
              ))}
            </ul>
          </div>
        </section>

        {/* RESUMEN + PAGO */}
        <aside className="od-side">
          <div className="od-card">
            <h2>Resumen</h2>
            <div className="od-row"><span>Subtotal</span><span>{pen.format(order.subtotal)}</span></div>
            {order.discount > 0 && (
              <div className="od-row"><span>Descuento</span><span>−{pen.format(order.discount)}</span></div>
            )}
            <div className="od-row"><span>Envío</span><span>{order.shippingCost === 0 ? 'Gratis' : pen.format(order.shippingCost)}</span></div>
            {isFactura && (
              <>
                <div className="od-row od-row--muted"><span>Op. gravada</span><span>{pen.format(base)}</span></div>
                <div className="od-row od-row--muted"><span>IGV (18%)</span><span>{pen.format(order.igv)}</span></div>
              </>
            )}
            {!isFactura && (
              <div className="od-row od-row--muted"><span>IGV incluido</span><span>{pen.format(order.igv)}</span></div>
            )}
            <div className="od-total"><span>Total</span><span>{pen.format(order.total)}</span></div>
          </div>

          {order.status === 'PENDIENTE' && (
            <div className="od-card od-pay">
              <h2>Pagar</h2>
              {error && <p className="ord-status od-pay__err">{error}</p>}
              <div className="od-methods">
                {PAY_METHODS.map((m) => (
                  <button
                    key={m.value}
                    type="button"
                    className={method === m.value ? 'od-method od-method--on' : 'od-method'}
                    onClick={() => setMethod(m.value)}
                  >
                    {m.label}
                  </button>
                ))}
              </div>

              {isCard && (
                <div className="od-payform">
                  <label className="od-payform__field">
                    <span>Número de tarjeta</span>
                    <input
                      inputMode="numeric"
                      value={cardNumber}
                      placeholder="1234 5678 9012 3456"
                      maxLength={23}
                      onChange={(e) => setCardNumber(formatCardNumber(e.target.value))}
                    />
                    {cardNumber.length > 0 && !cardNumberOk && (
                      <small className="od-payform__err">El número debe tener entre 13 y 19 dígitos.</small>
                    )}
                  </label>
                  <div className="od-payform__row">
                    <label className="od-payform__field">
                      <span>Vencimiento</span>
                      <input
                        inputMode="numeric"
                        value={cardExpiry}
                        placeholder="MM/AA"
                        onChange={(e) => setCardExpiry(formatExpiry(e.target.value))}
                      />
                      {cardExpiry.length > 0 && !cardExpiryOk && (
                        <small className="od-payform__err">Formato MM/AA (mes 01–12).</small>
                      )}
                    </label>
                    <label className="od-payform__field">
                      <span>CVV</span>
                      <input
                        inputMode="numeric"
                        value={cardCvv}
                        placeholder="123"
                        maxLength={4}
                        onChange={(e) => setCardCvv(onlyDigits(e.target.value).slice(0, 4))}
                      />
                      {cardCvv.length > 0 && !cardCvvOk && (
                        <small className="od-payform__err">3 o 4 dígitos.</small>
                      )}
                    </label>
                  </div>
                  <label className="od-payform__field">
                    <span>Titular de la tarjeta</span>
                    <input
                      type="text"
                      value={cardName}
                      placeholder="JUAN PEREZ"
                      maxLength={80}
                      onChange={(e) => setCardName(e.target.value)}
                    />
                  </label>
                </div>
              )}

              {method === 'YAPE' && (
                <div className="od-payform">
                  <label className="od-payform__field">
                    <span>Celular Yape</span>
                    <input
                      inputMode="numeric"
                      value={yapePhone}
                      placeholder="9 dígitos"
                      maxLength={9}
                      onChange={(e) => setYapePhone(onlyDigits(e.target.value).slice(0, 9))}
                    />
                    {yapePhone.length > 0 && yapePhone.length !== 9 && (
                      <small className="od-payform__err">El celular debe tener 9 dígitos.</small>
                    )}
                  </label>
                  <label className="od-payform__field">
                    <span>Código de aprobación</span>
                    <input
                      inputMode="numeric"
                      value={yapeCode}
                      placeholder="6 dígitos"
                      maxLength={6}
                      onChange={(e) => setYapeCode(onlyDigits(e.target.value).slice(0, 6))}
                    />
                    {yapeCode.length > 0 && yapeCode.length !== 6 && (
                      <small className="od-payform__err">El código debe tener 6 dígitos.</small>
                    )}
                  </label>
                </div>
              )}

              <p className="od-pay__sim"><Lock size={13} /> Pago simulado — no se realiza ningún cobro real.</p>
              <button type="button" className="od-pay__btn" onClick={pay} disabled={paying || !payValid}>
                <CreditCard size={18} /> {paying ? 'Procesando…' : `Pagar ${pen.format(order.total)}`}
              </button>
              {!payValid && !paying && (
                <p className="od-pay__hint">Completá los datos del pago para continuar.</p>
              )}
            </div>
          )}

          {isPaid && (
            <div className="od-card od-paid-card">
              {order.status === 'CONFIRMADA' && (
                <div className="od-paid"><Check size={18} /> Pago confirmado</div>
              )}
              <button type="button" className="od-dl" onClick={downloadComprobante} disabled={downloadingComp}>
                <Download size={17} /> {downloadingComp ? 'Generando…' : `Descargar ${isFactura ? 'factura' : 'boleta'}`}
              </button>
              {compError && <p className="ord-status od-pay__err">{compError}</p>}
            </div>
          )}
        </aside>
      </div>
    </div>
  );
}
