import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ArrowRight, Lock, ShieldCheck } from 'lucide-react';
import { useAuth } from '../../auth/AuthContext';
import { useCart } from '../../cart/CartContext';
import { checkout } from '../orders/orders.api';
import type { DocumentType } from '../../models/order';
import './checkout.css';

const pen = new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN', minimumFractionDigits: 2 });

/** Redondeo a 2 decimales (espejo del HALF_UP del backend para el preview). */
const round2 = (n: number) => Math.round(n * 100) / 100;

/**
 * Checkout: elige comprobante (boleta/factura) + datos del receptor y confirma.
 * El resumen (envío, IGV, total) es un PREVIEW que replica la regla del backend;
 * el total real lo calcula y persiste el backend al crear el pedido. Requiere sesión.
 */
export function CheckoutPage() {
  const { isAuthenticated } = useAuth();
  const { cart, refresh } = useCart();
  const navigate = useNavigate();

  const [documentType, setDocumentType] = useState<DocumentType>('BOLETA');
  const [customerName, setCustomerName] = useState('');
  const [customerDoc, setCustomerDoc] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 1) Sin sesión.
  if (!isAuthenticated) {
    return (
      <div className="ck ck-gate">
        <span className="ck-gate__ic"><Lock size={30} /></span>
        <h1>Iniciá sesión para continuar</h1>
        <Link to="/cuenta/ingresar" className="ck-gate__cta">Iniciar sesión</Link>
      </div>
    );
  }

  // 2) Carrito vacío → nada que pagar.
  if (!cart || cart.items.length === 0) {
    return (
      <div className="ck ck-gate">
        <h1>Tu carrito está vacío</h1>
        <p>Agregá productos antes de pagar.</p>
        <Link to="/catalogo" className="ck-gate__cta">Ir al catálogo</Link>
      </div>
    );
  }

  // Preview de montos (misma regla que el backend; el backend es la fuente de verdad).
  const subtotal = cart.total;
  const shipping = subtotal >= 300 ? 0 : 20;
  const total = subtotal + shipping;
  const base = round2(total / 1.18);
  const igv = round2(total - base);

  // Validación cliente: BOLETA → DNI 8 díg, FACTURA → RUC 11 díg (espejo del backend).
  const docLen = documentType === 'FACTURA' ? 11 : 8;
  const docValid = new RegExp(`^\\d{${docLen}}$`).test(customerDoc);
  const formValid = customerName.trim().length > 0 && docValid;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!formValid || submitting) return;
    setSubmitting(true);
    setError(null);
    try {
      const order = await checkout({ documentType, customerName: customerName.trim(), customerDoc });
      await refresh(); // el carrito quedó vacío en el backend
      navigate(`/pedidos/${order.id}`);
    } catch {
      setError('No se pudo completar el pedido. Revisá el stock disponible y los datos del comprobante.');
      setSubmitting(false);
    }
  };

  return (
    <div className="ck">
      <header className="ck-head"><h1>Finalizar compra</h1></header>

      {error && <p className="ck-alert">{error}</p>}

      <form className="ck-layout" onSubmit={submit}>
        {/* COMPROBANTE */}
        <section className="ck-form">
          <h2>Comprobante</h2>

          <div className="ck-toggle" role="tablist" aria-label="Tipo de comprobante">
            {(['BOLETA', 'FACTURA'] as DocumentType[]).map((t) => (
              <button
                key={t}
                type="button"
                role="tab"
                aria-selected={documentType === t}
                className={documentType === t ? 'ck-toggle__opt ck-toggle__opt--on' : 'ck-toggle__opt'}
                onClick={() => { setDocumentType(t); setCustomerDoc(''); }}
              >
                {t === 'BOLETA' ? 'Boleta' : 'Factura'}
              </button>
            ))}
          </div>

          <label className="ck-field">
            <span>{documentType === 'FACTURA' ? 'Razón social' : 'Nombre completo'}</span>
            <input
              type="text"
              value={customerName}
              maxLength={150}
              onChange={(e) => setCustomerName(e.target.value)}
              placeholder={documentType === 'FACTURA' ? 'ACME S.A.C.' : 'Juan Pérez'}
            />
          </label>

          <label className="ck-field">
            <span>{documentType === 'FACTURA' ? 'RUC' : 'DNI'}</span>
            <input
              type="text"
              inputMode="numeric"
              value={customerDoc}
              maxLength={docLen}
              onChange={(e) => setCustomerDoc(e.target.value.replace(/\D/g, ''))}
              placeholder={documentType === 'FACTURA' ? '11 dígitos' : '8 dígitos'}
            />
            {customerDoc.length > 0 && !docValid && (
              <small className="ck-field__err">
                {documentType === 'FACTURA' ? 'El RUC debe tener 11 dígitos.' : 'El DNI debe tener 8 dígitos.'}
              </small>
            )}
          </label>

          <p className="ck-note"><ShieldCheck size={15} /> El IGV (18%) ya está incluido en los precios.</p>
        </section>

        {/* RESUMEN */}
        <aside className="ck-summary">
          <h2>Resumen</h2>
          <div className="ck-summary__row"><span>Subtotal</span><span>{pen.format(subtotal)}</span></div>
          <div className="ck-summary__row">
            <span>Envío</span>
            <span>{shipping === 0 ? 'Gratis' : pen.format(shipping)}</span>
          </div>
          <div className="ck-summary__row ck-summary__row--muted">
            <span>IGV (incluido)</span><span>{pen.format(igv)}</span>
          </div>
          <div className="ck-summary__total"><span>Total</span><span>{pen.format(total)}</span></div>

          <button type="submit" className="ck-summary__pay" disabled={!formValid || submitting}>
            {submitting ? 'Procesando…' : <>Confirmar pedido <ArrowRight size={18} /></>}
          </button>
          <Link to="/carrito" className="ck-summary__back">Volver al carrito</Link>
        </aside>
      </form>
    </div>
  );
}
