import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  ArrowRight, BadgeCheck, Check, Percent, RotateCcw, ShieldCheck, ShoppingCart,
  Tag, Truck, Zap, type LucideIcon,
} from 'lucide-react';
import { featured, listCategories } from '../catalog/products.api';
import { iconForCategory } from '../../components/categoryIcon';
import { useAuth } from '../../auth/AuthContext';
import { useCart } from '../../cart/CartContext';
import { useComingSoon } from '../../components/coming-soon/ComingSoon';
import { PLACEHOLDER_IMAGE, type CategoryResponse, type ProductResponse } from '../../models/product';
import './home.css';

const perks: { Icon: LucideIcon; title: string; desc: string }[] = [
  { Icon: Truck, title: 'Envío en 24h', desc: 'Entrega express a todo el Perú' },
  { Icon: ShieldCheck, title: 'Pago seguro', desc: 'Transacciones 100% protegidas' },
  { Icon: BadgeCheck, title: 'Garantía oficial', desc: 'Productos con respaldo de marca' },
  { Icon: RotateCcw, title: 'Devoluciones', desc: '30 días para cambios y reembolsos' },
];

const pen = new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN', minimumFractionDigits: 2 });

export function HomePage() {
  const comingSoon = useComingSoon();
  const { isAuthenticated } = useAuth();
  const { addItem } = useCart();
  const navigate = useNavigate();
  const [products, setProducts] = useState<ProductResponse[]>([]);
  const [cats, setCats] = useState<CategoryResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [addedId, setAddedId] = useState<number | null>(null); // confirmación efímera (✓)

  useEffect(() => {
    featured(4)
      .then(setProducts)
      .catch(() => {})
      .finally(() => setLoading(false));
    listCategories().then(setCats).catch(() => {});
  }, []);

  // Agrega al carrito (igual que ProductCard); sin sesión, al login.
  const handleAdd = async (productId: number) => {
    if (!isAuthenticated) { navigate('/cuenta/ingresar'); return; }
    try {
      await addItem({ productId, quantity: 1 });
      setAddedId(productId);
      setTimeout(() => setAddedId((c) => (c === productId ? null : c)), 1400);
    } catch {
      // el backend valida el stock; sin feedback intrusivo en la home
    }
  };

  return (
    <div className="home">
      {/* HERO */}
      <section className="hero">
        <img className="hero__img" src="/bg-login.webp" alt="" />
        <div className="hero__tint" />
        <div className="orb" style={{ top: -160, right: -120, width: 520, height: 520, background: 'radial-gradient(circle, rgba(243,116,2,0.34), rgba(3,39,90,0) 64%)', animation: 'krFloat 12s ease-in-out infinite' }} />
        <div className="orb" style={{ bottom: -200, left: -120, width: 540, height: 540, background: 'radial-gradient(circle, rgba(26,125,215,0.40), rgba(3,39,90,0) 66%)', animation: 'krFloat2 14s ease-in-out infinite' }} />
        <div className="hero__inner">
          <div className="hero__content">
            <span className="hero__eyebrow"><Zap size={15} color="var(--kr-yellow-500)" /> Nueva temporada tech</span>
            <h1 className="hero__title">
              <span style={{ color: '#fff' }}>Tecnología<br />que&nbsp;</span>
              <span className="accent">enciende.</span>
            </h1>
            <p className="hero__sub">
              Laptops, componentes y audio de última generación. Arma tu setup con lo mejor de la
              tecnología — envíos a todo el Perú en 24 horas.
            </p>
            <div className="hero__ctas">
              <Link to="/catalogo" className="cta-pri">Explorar el catálogo <ArrowRight size={20} /></Link>
              <button type="button" className="ghost" onClick={() => comingSoon.show('Ofertas')}>
                <Tag size={19} color="var(--kr-yellow-500)" /> Ver ofertas
              </button>
            </div>
            <div className="hero__stats">
              <div><div className="stat-num">+1,200</div><div className="stat-lbl">productos en stock</div></div>
              <div className="stat-div" />
              <div><div className="stat-num">24h</div><div className="stat-lbl">entrega express</div></div>
              <div className="stat-div" />
              <div>
                <div className="stat-num" style={{ display: 'inline-flex', alignItems: 'baseline' }}>4.8<span style={{ marginLeft: 5, color: 'var(--kr-yellow-500)' }}>★</span></div>
                <div className="stat-lbl">valoración media</div>
              </div>
            </div>
          </div>
          <div className="hero__visual">
            <img src="/banner-productos.webp" alt="Parlante, speaker y gamepad Krypton" />
          </div>
        </div>
      </section>

      {/* CATEGORIES */}
      <section className="wrap" style={{ paddingTop: 64, paddingBottom: 8 }}>
        <div className="sec-head">
          <div><span className="eyebrow">Explora por categoría</span><h2 className="sec-title">¿Qué estás armando hoy?</h2></div>
          <Link to="/catalogo" className="see-all">Ver todo →</Link>
        </div>
        <div className="cat-grid">
          {cats.map((c) => {
            const Icon = iconForCategory(c.name);
            return (
              <Link key={c.id} to={`/catalogo?categoryId=${c.id}`} className="cat">
                <span className="cat__ic"><Icon size={26} /></span>
                <span className="cat__name">{c.name}</span>
              </Link>
            );
          })}
        </div>
      </section>

      {/* FEATURED (datos reales) */}
      <section className="wrap" style={{ paddingTop: 56, paddingBottom: 8 }}>
        <div className="sec-head">
          <div><span className="eyebrow">Lo más nuevo</span><h2 className="sec-title">Destacados del catálogo</h2></div>
          <Link to="/catalogo" className="see-all">Ver catálogo completo →</Link>
        </div>
        {loading ? (
          <p className="muted">Cargando destacados…</p>
        ) : products.length === 0 ? (
          <p className="muted">Aún no hay productos para mostrar.</p>
        ) : (
          <div className="prod-grid">
            {products.map((p) => (
              <article key={p.id} className="card">
                <Link to={`/catalogo/${p.id}`} className="card__media">
                  <img src={p.imageUrl ?? PLACEHOLDER_IMAGE} alt={p.name} />
                </Link>
                <div className="card__body">
                  <span className="card__cat">{p.categoryName}</span>
                  <Link to={`/catalogo/${p.id}`} className="card__name-link"><h3 className="card__name">{p.name}</h3></Link>
                  <div className="card__foot">
                    <span className="card__price">{pen.format(p.price)}</span>
                    <button className={addedId === p.id ? 'card__add card__add--ok' : 'card__add'} type="button" aria-label="Agregar al carrito" onClick={() => handleAdd(p.id)}>
                      {addedId === p.id ? <Check size={18} /> : <ShoppingCart size={18} />}
                    </button>
                  </div>
                </div>
              </article>
            ))}
          </div>
        )}
      </section>

      {/* PROMO */}
      <section className="wrap" style={{ marginTop: 64 }}>
        <div className="promo">
          <div className="orb" style={{ top: -120, right: '14%', width: 340, height: 340, background: 'radial-gradient(circle, rgba(242,184,9,0.22), rgba(3,39,90,0) 70%)', animation: 'krFloat 13s ease-in-out infinite' }} />
          <div style={{ position: 'relative', zIndex: 2, color: '#fff', maxWidth: 560 }}>
            <span className="promo__eyebrow"><Percent size={15} /> Oferta de temporada</span>
            <h2 className="promo__title">Hasta <span className="accent">30% de dscto.</span> en setups gamer</h2>
            <p className="promo__sub">Arma tu rig completo: GPU, monitor, periféricos y audio con descuentos exclusivos por tiempo limitado.</p>
          </div>
          <button type="button" className="cta-pri" style={{ position: 'relative', zIndex: 2, height: 58 }} onClick={() => comingSoon.show('Ofertas')}>
            Aprovechar ofertas <ArrowRight size={20} />
          </button>
        </div>
      </section>

      {/* TRUST */}
      <section className="wrap" style={{ paddingTop: 64, paddingBottom: 72 }}>
        <div className="trust-grid">
          {perks.map((pk) => (
            <div key={pk.title} className="perk">
              <span className="perk__ic"><pk.Icon size={23} /></span>
              <div><div className="perk__title">{pk.title}</div><div className="perk__desc">{pk.desc}</div></div>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
