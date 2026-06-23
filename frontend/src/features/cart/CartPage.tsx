import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, Lock, Minus, Plus, ShoppingBag, Trash2 } from 'lucide-react';
import { useAuth } from '../../auth/AuthContext';
import { useCart } from '../../cart/CartContext';
import { PLACEHOLDER_IMAGE } from '../../models/product';
import './cart.css';

const pen = new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN', minimumFractionDigits: 2 });

/**
 * Carrito del usuario. Estado global vía useCart; el backend lo persiste por
 * usuario, así que requiere sesión. El checkout es otra vista (por ahora stub).
 */
export function CartPage() {
  const { isAuthenticated } = useAuth();
  const { cart, itemCount, loading, updateItem, removeItem, clear } = useCart();
  const [mutating, setMutating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Envuelve cada mutación: bloquea controles (evita carreras) y captura errores.
  const run = async (fn: () => Promise<void>) => {
    setMutating(true);
    setError(null);
    try {
      await fn();
    } catch {
      setError('No se pudo actualizar el carrito. Revisá el stock disponible e intentá de nuevo.');
    } finally {
      setMutating(false);
    }
  };

  // 1) Sin sesión: el carrito es por usuario.
  if (!isAuthenticated) {
    return (
      <div className="cart cart-gate">
        <span className="cart-gate__ic"><Lock size={30} /></span>
        <h1>Iniciá sesión para ver tu carrito</h1>
        <p>Tu carrito está asociado a tu cuenta. Ingresá para agregar productos y completar tu compra.</p>
        <div className="cart-gate__actions">
          <Link to="/cuenta/ingresar" className="cart-gate__cta">Iniciar sesión</Link>
          <Link to="/catalogo" className="cart-gate__link">Seguir viendo productos</Link>
        </div>
      </div>
    );
  }

  // 2) Primera carga.
  if (loading && !cart) {
    return <div className="cart"><p className="cart-status">Cargando tu carrito…</p></div>;
  }

  // 3) Vacío.
  if (!cart || cart.items.length === 0) {
    return (
      <div className="cart cart-empty">
        <span className="cart-empty__ic"><ShoppingBag size={34} /></span>
        <h1>Tu carrito está vacío</h1>
        <p>Todavía no agregaste productos. Explorá el catálogo y encontrá lo que buscás.</p>
        <Link to="/catalogo" className="cart-empty__cta">Ir al catálogo</Link>
      </div>
    );
  }

  // 4) Con ítems.
  return (
    <div className="cart">
      <header className="cart-head">
        <h1>Tu carrito</h1>
        <span className="cart-head__count">{itemCount} {itemCount === 1 ? 'producto' : 'productos'}</span>
      </header>

      {error && <p className="cart-alert">{error}</p>}

      <div className="cart-layout">
        {/* LÍNEAS */}
        <ul className="cart-items">
          {cart.items.map((it) => (
            <li key={it.itemId} className="cart-item">
              <Link to={`/catalogo/${it.productId}`} className="cart-item__media">
                <img src={PLACEHOLDER_IMAGE} alt={it.productName} />
              </Link>
              <div className="cart-item__info">
                <Link to={`/catalogo/${it.productId}`} className="cart-item__name">{it.productName}</Link>
                <span className="cart-item__sku">SKU: {it.sku}</span>
                <span className="cart-item__unit">{pen.format(it.price)} c/u</span>
              </div>
              <div className="cart-item__qty" aria-label="Cantidad">
                <button type="button" onClick={() => run(() => updateItem(it.itemId, it.quantity - 1))} disabled={mutating || it.quantity <= 1} aria-label="Quitar uno">
                  <Minus size={15} />
                </button>
                <span>{it.quantity}</span>
                <button type="button" onClick={() => run(() => updateItem(it.itemId, it.quantity + 1))} disabled={mutating} aria-label="Agregar uno">
                  <Plus size={15} />
                </button>
              </div>
              <span className="cart-item__subtotal">{pen.format(it.subtotal)}</span>
              <button type="button" className="cart-item__remove" onClick={() => run(() => removeItem(it.itemId))} disabled={mutating} aria-label="Quitar del carrito">
                <Trash2 size={18} />
              </button>
            </li>
          ))}
        </ul>

        {/* RESUMEN */}
        <aside className="cart-summary">
          <h2>Resumen</h2>
          <div className="cart-summary__row">
            <span>Subtotal ({itemCount} {itemCount === 1 ? 'ítem' : 'ítems'})</span>
            <span>{pen.format(cart.total)}</span>
          </div>
          <div className="cart-summary__row cart-summary__row--muted">
            <span>Envío</span>
            <span>Se calcula al pagar</span>
          </div>
          <div className="cart-summary__total">
            <span>Total</span>
            <span>{pen.format(cart.total)}</span>
          </div>
          <Link to="/checkout" className="cart-summary__pay">
            Proceder al pago <ArrowRight size={18} />
          </Link>
          <button type="button" className="cart-summary__clear" onClick={() => run(clear)} disabled={mutating}>
            Vaciar carrito
          </button>
          <Link to="/catalogo" className="cart-summary__continue">Seguir comprando</Link>
        </aside>
      </div>
    </div>
  );
}
