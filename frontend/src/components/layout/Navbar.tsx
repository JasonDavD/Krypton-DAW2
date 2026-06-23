import { useEffect, useRef, useState, type KeyboardEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ChevronDown, Search, ShoppingCart, Truck, X } from 'lucide-react';
import { useAuth } from '../../auth/AuthContext';
import { useCart } from '../../cart/CartContext';
import { useComingSoon } from '../coming-soon/ComingSoon';
import { listCategories } from '../../features/catalog/products.api';
import type { CategoryResponse } from '../../models/product';
import './navbar.css';

/**
 * Chrome superior: barra de anuncio + navbar sticky (marca Krypton).
 * Auth-aware (login/registro vs usuario + logout). La lupa despliega un buscador
 * animado; Enter navega a /catalogo?q=term (el catálogo lo aplica como filtro).
 */
export function Navbar() {
  const { isAuthenticated, isAdmin, user, logout } = useAuth();
  const { itemCount } = useCart();
  const comingSoon = useComingSoon();
  const navigate = useNavigate();
  const [searchOpen, setSearchOpen] = useState(false);
  const [query, setQuery] = useState('');
  const inputRef = useRef<HTMLInputElement>(null);

  // Menú desplegable de categorías (cargadas del backend).
  const [catOpen, setCatOpen] = useState(false);
  const [categories, setCategories] = useState<CategoryResponse[]>([]);
  const catRef = useRef<HTMLDivElement>(null);

  // Categorías para el menú (una sola vez).
  useEffect(() => {
    listCategories().then(setCategories).catch(() => {});
  }, []);

  // Cerrar el menú al hacer click afuera o con Escape.
  useEffect(() => {
    if (!catOpen) return;
    const onDown = (e: MouseEvent) => {
      if (catRef.current && !catRef.current.contains(e.target as Node)) setCatOpen(false);
    };
    const onEsc = (e: globalThis.KeyboardEvent) => {
      if (e.key === 'Escape') setCatOpen(false);
    };
    document.addEventListener('mousedown', onDown);
    document.addEventListener('keydown', onEsc);
    return () => {
      document.removeEventListener('mousedown', onDown);
      document.removeEventListener('keydown', onEsc);
    };
  }, [catOpen]);

  useEffect(() => {
    if (!searchOpen) return;
    const el = inputRef.current;
    if (!el) return;
    el.focus();
    el.animate(
      [{ transform: 'scaleX(0.25)', opacity: 0.35 }, { transform: 'scaleX(1)', opacity: 1 }],
      { duration: 300, easing: 'cubic-bezier(0.22,1,0.36,1)' },
    );
  }, [searchOpen]);

  const submitSearch = () => {
    const q = query.trim();
    navigate(q ? `/catalogo?q=${encodeURIComponent(q)}` : '/catalogo');
    setSearchOpen(false);
  };

  const onKey = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Escape') setSearchOpen(false);
    if (e.key === 'Enter') submitSearch();
  };

  return (
    <>
      <div className="nv-ann">
        <Truck size={16} /> Envío gratis en compras desde S/ 199 — solo esta semana
      </div>

      <div className="nv-bar">
        <header className="nv-nav">
          <Link to="/" aria-label="Krypton inicio">
            <img className="nv-logo" src="/brand/Krypton-navy.svg" alt="Krypton" />
          </Link>

          {!searchOpen && (
            <nav className="nv-links">
              <Link to="/catalogo" className="nv-link">Catálogo</Link>
              <div className="nv-drop" ref={catRef}>
                <button
                  type="button"
                  className={catOpen ? 'nv-link nv-link--open' : 'nv-link'}
                  onClick={() => setCatOpen((o) => !o)}
                  aria-haspopup="true"
                  aria-expanded={catOpen}
                >
                  Categorías <ChevronDown size={15} className="nv-drop__caret" />
                </button>
                {catOpen && (
                  <div className="nv-menu" role="menu">
                    {categories.length === 0 ? (
                      <span className="nv-menu__empty">Cargando…</span>
                    ) : (
                      categories.map((c) => (
                        <Link
                          key={c.id}
                          to={`/catalogo?categoryId=${c.id}`}
                          className="nv-menu__item"
                          role="menuitem"
                          onClick={() => setCatOpen(false)}
                        >
                          {c.name}
                        </Link>
                      ))
                    )}
                  </div>
                )}
              </div>
              <button type="button" className="nv-link" onClick={() => comingSoon.show('Ofertas')}>Ofertas</button>
              {isAuthenticated && <Link to="/pedidos" className="nv-link">Mis pedidos</Link>}
            </nav>
          )}

          <div className="nv-actions">
            {searchOpen && (
              <input
                ref={inputRef}
                className="nv-search"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                onKeyDown={onKey}
                placeholder="Buscar productos por nombre…"
              />
            )}
            <button
              type="button"
              className={searchOpen ? 'nv-iconbtn nv-iconbtn--active' : 'nv-iconbtn'}
              onClick={() => setSearchOpen((o) => !o)}
              aria-label="Buscar"
            >
              {searchOpen ? <X size={19} /> : <Search size={19} />}
            </button>

            <Link to="/carrito" className="nv-iconbtn nv-cart" aria-label={`Carrito (${itemCount})`}>
              <ShoppingCart size={19} />
              {itemCount > 0 && <span className="nv-cart__badge">{itemCount > 99 ? '99+' : itemCount}</span>}
            </Link>

            {isAuthenticated ? (
              <>
                <span className="nv-user">{user?.email}</span>
                {isAdmin && <Link to="/admin" className="nv-link">Admin</Link>}
                <button type="button" className="nv-login" onClick={logout}>Cerrar sesión</button>
              </>
            ) : (
              <>
                <Link to="/cuenta/ingresar" className="nv-login">Iniciar sesión</Link>
                <Link to="/cuenta/registro" className="nv-cta">Crear cuenta</Link>
              </>
            )}
          </div>
        </header>
      </div>
    </>
  );
}
