import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Check, ShoppingCart } from 'lucide-react';
import { useAuth } from '../auth/AuthContext';
import { useCart } from '../cart/CartContext';
import { PLACEHOLDER_IMAGE, type ProductResponse } from '../models/product';
import './product-card.css';

const pen = new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN', minimumFractionDigits: 2 });

/** Card de producto reutilizable (catálogo, destacados, etc.). */
export function ProductCard({ product }: { product: ProductResponse }) {
  const { isAuthenticated } = useAuth();
  const { addItem } = useCart();
  const navigate = useNavigate();
  const [adding, setAdding] = useState(false);
  const [added, setAdded] = useState(false); // confirmación efímera (✓)

  const handleAdd = async () => {
    // El carrito es por usuario: sin sesión, al login.
    if (!isAuthenticated) { navigate('/cuenta/ingresar'); return; }
    setAdding(true);
    try {
      await addItem({ productId: product.id, quantity: 1 });
      setAdded(true);
      setTimeout(() => setAdded(false), 1400);
    } catch {
      // el backend rechaza si no alcanza el stock; sin feedback intrusivo en la card
    } finally {
      setAdding(false);
    }
  };

  return (
    <article className="pc-card">
      <Link to={`/catalogo/${product.id}`} className="pc-media">
        <img src={product.imageUrl ?? PLACEHOLDER_IMAGE} alt={product.name} />
      </Link>
      <div className="pc-body">
        <span className="pc-cat">{product.categoryName}</span>
        <Link to={`/catalogo/${product.id}`} className="pc-name-link"><h3 className="pc-name">{product.name}</h3></Link>
        <div className="pc-foot">
          <span className="pc-price">{pen.format(product.price)}</span>
          <button
            className={added ? 'pc-add pc-add--ok' : 'pc-add'}
            type="button"
            aria-label="Agregar al carrito"
            disabled={adding}
            onClick={handleAdd}
          >
            {added ? <Check size={18} /> : <ShoppingCart size={18} />}
          </button>
        </div>
      </div>
    </article>
  );
}
