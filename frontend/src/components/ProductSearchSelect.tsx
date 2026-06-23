import { useEffect, useRef, useState } from 'react';
import { Search, X } from 'lucide-react';
import { search } from '../features/catalog/products.api';
import type { ProductResponse } from '../models/product';
import './product-search-select.css';

/**
 * Selector de producto con búsqueda (autocomplete). Reutilizable: en vez de un
 * <select> con TODOS los productos (no escala), tipeás y filtra contra el backend
 * (`GET /api/products?name=`) con debounce. value/onChange como un input controlado.
 */
export function ProductSearchSelect({ value, onChange, placeholder = 'Buscar producto…' }: {
  value: ProductResponse | null;
  onChange: (product: ProductResponse | null) => void;
  placeholder?: string;
}) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<ProductResponse[]>([]);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  // Búsqueda con debounce (un fetch al dejar de tipear).
  useEffect(() => {
    if (query.trim() === '') { setResults([]); return; }
    const t = setTimeout(() => {
      setLoading(true);
      search({ name: query.trim() }, 0, 8)
        .then((r) => setResults(r.content))
        .catch(() => setResults([]))
        .finally(() => setLoading(false));
    }, 300);
    return () => clearTimeout(t);
  }, [query]);

  // Cerrar el dropdown al clickear fuera.
  useEffect(() => {
    const onDoc = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onDoc);
    return () => document.removeEventListener('mousedown', onDoc);
  }, []);

  const pick = (p: ProductResponse) => {
    onChange(p);
    setQuery('');
    setOpen(false);
  };

  return (
    <div className="pss" ref={ref}>
      {value ? (
        <div className="pss-selected">
          <span className="pss-selected__name">{value.name}</span>
          <button type="button" onClick={() => { onChange(null); setQuery(''); setResults([]); }} aria-label="Quitar selección">
            <X size={15} />
          </button>
        </div>
      ) : (
        <div className="pss-input">
          <Search size={16} />
          <input
            value={query}
            onChange={(e) => { setQuery(e.target.value); setOpen(true); }}
            onFocus={() => setOpen(true)}
            placeholder={placeholder}
          />
        </div>
      )}

      {open && !value && query.trim() !== '' && (
        <ul className="pss-list">
          {loading ? (
            <li className="pss-msg">Buscando…</li>
          ) : results.length === 0 ? (
            <li className="pss-msg">Sin resultados</li>
          ) : (
            results.map((p) => (
              <li key={p.id}>
                <button type="button" className="pss-opt" onClick={() => pick(p)}>
                  <span className="pss-opt__name">{p.name}</span>
                  <span className="pss-opt__sku">{p.sku}</span>
                </button>
              </li>
            ))
          )}
        </ul>
      )}
    </div>
  );
}
