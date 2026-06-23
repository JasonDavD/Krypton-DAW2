import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  Check, ChevronLeft, ChevronRight, Minus, PackageX, Plus, ShieldCheck,
  ShoppingCart, Truck,
} from 'lucide-react';
import { getById } from './products.api';
import { useAuth } from '../../auth/AuthContext';
import { useCart } from '../../cart/CartContext';
import { PLACEHOLDER_IMAGE, type ProductResponse } from '../../models/product';
import './product-detail.css';

const pen = new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN', minimumFractionDigits: 2 });

/**
 * Detalle de producto: galería (carrusel) + panel de información.
 * La galería usa product.images (presente solo en GET /api/products/{id}); si
 * no hay imágenes cae al imageUrl y, en último caso, al placeholder de marca.
 */
export function ProductDetailPage() {
  const { id } = useParams();
  const { isAuthenticated } = useAuth();
  const { addItem } = useCart();
  const navigate = useNavigate();

  const [product, setProduct] = useState<ProductResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [active, setActive] = useState(0); // índice de la imagen visible
  const [qty, setQty] = useState(1);
  const [adding, setAdding] = useState(false);
  const [added, setAdded] = useState(false);
  const [addError, setAddError] = useState(false);

  useEffect(() => {
    const productId = Number(id);
    if (!Number.isFinite(productId)) {
      setNotFound(true);
      setLoading(false);
      return;
    }
    setLoading(true);
    setNotFound(false);
    setActive(0);
    setQty(1);
    getById(productId)
      .then(setProduct)
      .catch(() => setNotFound(true))
      .finally(() => setLoading(false));
  }, [id]);

  if (loading) {
    return <div className="pd"><p className="pd-status">Cargando producto…</p></div>;
  }
  if (notFound || !product) {
    return (
      <div className="pd pd-missing">
        <h1>Producto no encontrado</h1>
        <p>El producto que buscás no existe o ya no está disponible.</p>
        <Link to="/catalogo" className="pd-missing__cta">Volver al catálogo</Link>
      </div>
    );
  }

  // Galería: images[] (solo en detalle) → imageUrl → placeholder de marca.
  const gallery = product.images && product.images.length > 0
    ? product.images.map((img) => img.url)
    : product.imageUrl
      ? [product.imageUrl]
      : [PLACEHOLDER_IMAGE];
  const multi = gallery.length > 1;
  const inStock = product.stock > 0;

  const prev = () => setActive((i) => (i - 1 + gallery.length) % gallery.length);
  const next = () => setActive((i) => (i + 1) % gallery.length);

  const handleAdd = async () => {
    // El carrito es por usuario: sin sesión, al login.
    if (!isAuthenticated) { navigate('/cuenta/ingresar'); return; }
    setAdding(true);
    setAddError(false);
    try {
      await addItem({ productId: product.id, quantity: qty });
      setAdded(true);
      setTimeout(() => setAdded(false), 1800);
    } catch {
      setAddError(true); // p.ej. stock insuficiente (lo valida el backend)
    } finally {
      setAdding(false);
    }
  };

  return (
    <div className="pd">
      <nav className="pd-crumb" aria-label="Migas">
        <Link to="/">Inicio</Link><span>/</span>
        <Link to="/catalogo">Catálogo</Link><span>/</span>
        <strong>{product.name}</strong>
      </nav>

      <div className="pd-grid">
        {/* GALERÍA */}
        <section className="pd-gallery">
          <div className="pd-stage">
            <img src={gallery[active]} alt={product.name} />
            {multi && (
              <>
                <button type="button" className="pd-nav pd-nav--prev" onClick={prev} aria-label="Imagen anterior">
                  <ChevronLeft size={22} />
                </button>
                <button type="button" className="pd-nav pd-nav--next" onClick={next} aria-label="Imagen siguiente">
                  <ChevronRight size={22} />
                </button>
              </>
            )}
          </div>
          {multi && (
            <div className="pd-thumbs">
              {gallery.map((src, i) => (
                <button
                  key={i}
                  type="button"
                  className={i === active ? 'pd-thumb pd-thumb--on' : 'pd-thumb'}
                  onClick={() => setActive(i)}
                  aria-label={`Ver imagen ${i + 1}`}
                >
                  <img src={src} alt="" />
                </button>
              ))}
            </div>
          )}
        </section>

        {/* INFO */}
        <section className="pd-info">
          <Link to={`/catalogo?categoryId=${product.categoryId}`} className="pd-cat">{product.categoryName}</Link>
          <h1 className="pd-name">{product.name}</h1>

          <div className="pd-price-row">
            <span className="pd-price">{pen.format(product.price)}</span>
            <span className={inStock ? 'pd-stock pd-stock--ok' : 'pd-stock pd-stock--out'}>
              {inStock
                ? <><Check size={15} /> En stock · {product.stock} disp.</>
                : <><PackageX size={15} /> Sin stock</>}
            </span>
          </div>

          {product.description && <p className="pd-desc">{product.description}</p>}

          <div className="pd-buy">
            <div className="pd-qty" aria-label="Cantidad">
              <button type="button" onClick={() => setQty((n) => Math.max(1, n - 1))} disabled={!inStock || qty <= 1} aria-label="Quitar uno">
                <Minus size={16} />
              </button>
              <span>{qty}</span>
              <button type="button" onClick={() => setQty((n) => Math.min(product.stock, n + 1))} disabled={!inStock || qty >= product.stock} aria-label="Agregar uno">
                <Plus size={16} />
              </button>
            </div>
            <button
              type="button"
              className={added ? 'pd-add pd-add--ok' : 'pd-add'}
              disabled={!inStock || adding}
              onClick={handleAdd}
            >
              {added
                ? <><Check size={19} /> Agregado al carrito</>
                : <><ShoppingCart size={19} /> {adding ? 'Agregando…' : 'Agregar al carrito'}</>}
            </button>
          </div>
          {addError && <p className="pd-add-err">No se pudo agregar — puede que no haya stock suficiente. Probá con menos cantidad.</p>}

          <div className="pd-trust">
            <span><ShieldCheck size={17} /> Garantía oficial</span>
            <span><Truck size={17} /> Envío a todo el país</span>
          </div>

          <dl className="pd-meta">
            <div><dt>SKU</dt><dd>{product.sku}</dd></div>
            <div><dt>Categoría</dt><dd>{product.categoryName}</dd></div>
          </dl>
        </section>
      </div>
    </div>
  );
}
