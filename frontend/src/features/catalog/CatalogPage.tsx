import { useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { ChevronLeft, ChevronRight, Headset, LayoutGrid, PackageSearch, Search } from 'lucide-react';
import { listCategories, search } from './products.api';
import { iconForCategory } from '../../components/categoryIcon';
import { ProductCard } from '../../components/ProductCard';
import { useComingSoon } from '../../components/coming-soon/ComingSoon';
import type { CategoryResponse, ProductResponse } from '../../models/product';
import './catalog.css';

const PRICE_MAX = 5000;
const pen = new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN', minimumFractionDigits: 2 });

type Sort = 'relevancia' | 'precio-asc' | 'precio-desc';

/**
 * Catálogo: sidebar de filtros + grilla de productos.
 * Filtros server-side (búsqueda ?q, categoría, precio máx.) vía search();
 * el orden se aplica sobre la página cargada (el backend aún no expone `sort`).
 */
export function CatalogPage() {
  const [params, setParams] = useSearchParams();
  const q = params.get('q') ?? '';
  const comingSoon = useComingSoon();

  // categoryId vive en la URL (?categoryId=X) → fuente de verdad: lo setea el
  // dropdown del navbar y el sidebar, y queda bookmarkeable/compartible.
  const categoryId = params.get('categoryId') ? Number(params.get('categoryId')) : undefined;
  const [priceMax, setPriceMax] = useState(PRICE_MAX);          // valor inmediato: slider + label
  const [priceMaxQuery, setPriceMaxQuery] = useState(PRICE_MAX); // valor debounced que dispara el fetch
  const [inStock, setInStock] = useState(false);
  const [sort, setSort] = useState<Sort>('relevancia');
  const [page, setPage] = useState(0);

  const [products, setProducts] = useState<ProductResponse[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [categories, setCategories] = useState<CategoryResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  // Categorías (una vez) para el sidebar.
  useEffect(() => {
    listCategories().then(setCategories).catch(() => {});
  }, []);

  // Debounce del precio: el slider mueve el thumb y el label al instante, pero la
  // búsqueda server-side espera 350ms tras el último ajuste → un solo fetch al soltar,
  // en vez de uno por cada paso del slider (era lo que hacía parpadear la página).
  useEffect(() => {
    const t = setTimeout(() => setPriceMaxQuery(priceMax), 350);
    return () => clearTimeout(t);
  }, [priceMax]);

  // Volver a página 0 cuando cambian búsqueda / categoría / precio (ya debounced).
  useEffect(() => {
    setPage(0);
  }, [q, categoryId, priceMaxQuery]);

  // Buscar productos (server-side) cuando cambian filtros o página.
  useEffect(() => {
    setLoading(true);
    setError(false);
    search({ name: q || undefined, categoryId, priceMax: priceMaxQuery < PRICE_MAX ? priceMaxQuery : undefined }, page)
      .then((res) => {
        setProducts(res.content);
        setTotalPages(res.totalPages);
        setTotalElements(res.totalElements);
      })
      .catch(() => setError(true))
      .finally(() => setLoading(false));
  }, [q, categoryId, priceMaxQuery, page]);

  // Refinamientos client-side sobre la página cargada (stock + orden).
  const visible = useMemo(() => {
    let list = inStock ? products.filter((p) => p.stock > 0) : products;
    if (sort === 'precio-asc') list = [...list].sort((a, b) => a.price - b.price);
    else if (sort === 'precio-desc') list = [...list].sort((a, b) => b.price - a.price);
    return list;
  }, [products, inStock, sort]);

  const activeCategory = categories.find((c) => c.id === categoryId);
  const heading = q ? `Resultados para “${q}”` : activeCategory ? activeCategory.name : 'Todos los productos';
  const resultLabel = totalElements === 1 ? '1 producto encontrado' : `${totalElements} productos encontrados`;

  // "Cargando…" sólo en la PRIMERA carga; en recargas mantenemos la grilla montada
  // (con un fundido) para no colapsar el alto de la página y evitar el salto de scroll.
  const showSkeleton = loading && products.length === 0;

  const setQuery = (value: string) => {
    const next = new URLSearchParams(params);
    if (value.trim()) next.set('q', value);
    else next.delete('q');
    setParams(next);
  };

  const setCategory = (id?: number) => {
    const next = new URLSearchParams(params);
    if (id != null) next.set('categoryId', String(id));
    else next.delete('categoryId');
    setParams(next);
  };

  const clearAll = () => {
    setParams({}); // limpia q y categoryId de la URL
    setPriceMax(PRICE_MAX);
    setInStock(false);
    setSort('relevancia');
  };

  const hasFilters = !!q || categoryId != null || priceMax < PRICE_MAX || inStock;

  return (
    <div className="ctl">
      {/* HEADER */}
      <div className="ctl-head">
        <div>
          <nav className="ctl-crumb" aria-label="Migas">
            <Link to="/">Inicio</Link><span>/</span><strong>Catálogo</strong>
          </nav>
          <h1 className="ctl-title">{heading}</h1>
          <p className="ctl-count">{loading ? 'Cargando…' : resultLabel}</p>
        </div>
        <label className="ctl-sort">
          <span>Ordenar por</span>
          <select value={sort} onChange={(e) => setSort(e.target.value as Sort)}>
            <option value="relevancia">Relevancia</option>
            <option value="precio-asc">Precio: menor a mayor</option>
            <option value="precio-desc">Precio: mayor a menor</option>
          </select>
        </label>
      </div>

      {/* LAYOUT */}
      <div className="ctl-layout">
        {/* SIDEBAR */}
        <aside className="ctl-side">
          <div className="ctl-side__head">
            <span>Filtros</span>
            <button type="button" className="ctl-clear" onClick={clearAll}>Limpiar todo</button>
          </div>

          <div className="ctl-search">
            <Search size={17} />
            <input value={q} onChange={(e) => setQuery(e.target.value)} placeholder="Buscar en catálogo…" />
          </div>

          <div>
            <div className="ctl-side__label">Categoría</div>
            <div className="ctl-cats">
              <button
                type="button"
                className={categoryId == null ? 'ctl-cat ctl-cat--on' : 'ctl-cat'}
                onClick={() => setCategory(undefined)}
              >
                <span className="ctl-cat__name"><LayoutGrid size={17} />Todas las categorías</span>
              </button>
              {categories.map((c) => {
                const Icon = iconForCategory(c.name);
                const on = categoryId === c.id;
                return (
                  <button
                    key={c.id}
                    type="button"
                    className={on ? 'ctl-cat ctl-cat--on' : 'ctl-cat'}
                    onClick={() => setCategory(c.id)}
                  >
                    <span className="ctl-cat__name"><Icon size={17} />{c.name}</span>
                  </button>
                );
              })}
            </div>
          </div>

          <div>
            <div className="ctl-side__row">
              <span className="ctl-side__label">Precio máx.</span>
              <span className="ctl-price-val">{priceMax >= PRICE_MAX ? 'Sin límite' : pen.format(priceMax)}</span>
            </div>
            <input
              className="ctl-range"
              type="range"
              min={0}
              max={PRICE_MAX}
              step={100}
              value={priceMax}
              onChange={(e) => setPriceMax(Number(e.target.value))}
            />
            <div className="ctl-range__ends"><span>S/ 0</span><span>S/ 5,000</span></div>
          </div>

          <label className="ctl-check">
            <input type="checkbox" checked={inStock} onChange={(e) => setInStock(e.target.checked)} />
            <span>Solo productos en stock</span>
          </label>

          <button type="button" className="ctl-help" style={{ cursor: 'pointer', fontFamily: 'var(--font-sans)' }} onClick={() => comingSoon.show('Centro de ayuda')}><Headset size={17} /> ¿Necesitas ayuda?</button>
        </aside>

        {/* RESULTS */}
        <div className="ctl-results">
          {hasFilters && (
            <div className="ctl-chips">
              <span className="ctl-chips__label">Filtros activos:</span>
              {q && <button type="button" className="ctl-chip" onClick={() => setQuery('')}>“{q}” <i>×</i></button>}
              {activeCategory && <button type="button" className="ctl-chip" onClick={() => setCategory(undefined)}>{activeCategory.name} <i>×</i></button>}
              {priceMax < PRICE_MAX && <button type="button" className="ctl-chip" onClick={() => setPriceMax(PRICE_MAX)}>Hasta {pen.format(priceMax)} <i>×</i></button>}
              {inStock && <button type="button" className="ctl-chip" onClick={() => setInStock(false)}>En stock <i>×</i></button>}
            </div>
          )}

          {showSkeleton ? (
            <p className="ctl-status">Cargando productos…</p>
          ) : error ? (
            <p className="ctl-status ctl-status--err">No se pudieron cargar los productos.</p>
          ) : visible.length === 0 ? (
            <div className="ctl-empty">
              <span className="ctl-empty__ic"><PackageSearch size={34} /></span>
              <h3>No se encontraron productos</h3>
              <p>Prueba ajustando los filtros o limpiando la búsqueda para ver más resultados.</p>
              <button type="button" className="ctl-empty__cta" onClick={clearAll}>Limpiar filtros</button>
            </div>
          ) : (
            <>
              <div className={loading ? 'ctl-grid ctl-grid--busy' : 'ctl-grid'}>
                {visible.map((p) => <ProductCard key={p.id} product={p} />)}
              </div>

              {totalPages > 1 && (
                <nav className="ctl-pages" aria-label="Paginación">
                  <button type="button" className="ctl-page ctl-page--ic" disabled={page === 0} onClick={() => setPage((n) => Math.max(0, n - 1))}>
                    <ChevronLeft size={18} />
                  </button>
                  {Array.from({ length: totalPages }, (_, i) => i).map((n) => (
                    <button
                      key={n}
                      type="button"
                      className={n === page ? 'ctl-page ctl-page--on' : 'ctl-page'}
                      onClick={() => setPage(n)}
                    >
                      {n + 1}
                    </button>
                  ))}
                  <button type="button" className="ctl-page ctl-page--ic" disabled={page >= totalPages - 1} onClick={() => setPage((n) => Math.min(totalPages - 1, n + 1))}>
                    <ChevronRight size={18} />
                  </button>
                </nav>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}
