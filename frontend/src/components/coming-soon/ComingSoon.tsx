import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { Construction } from 'lucide-react';
import './coming-soon.css';

/**
 * Card flotante "Pendiente por implementar" para acciones aún sin construir.
 * Provider global + hook useComingSoon().show('Nombre'). Cierra por backdrop,
 * botón "Entendido" o Escape.
 */
interface ComingSoonValue {
  show: (feature?: string) => void;
  hide: () => void;
}

const ComingSoonContext = createContext<ComingSoonValue | undefined>(undefined);

export function ComingSoonProvider({ children }: { children: ReactNode }) {
  const [feature, setFeature] = useState<string | null>(null);

  const show = useCallback((f = '') => setFeature(f), []);
  const hide = useCallback(() => setFeature(null), []);

  useEffect(() => {
    if (feature === null) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') hide();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [feature, hide]);

  return (
    <ComingSoonContext.Provider value={{ show, hide }}>
      {children}
      {feature !== null && (
        <div className="cs-backdrop" role="dialog" aria-modal="true" onClick={hide}>
          <div className="cs-card" onClick={(e) => e.stopPropagation()}>
            <span className="cs-icon">
              <Construction size={32} />
            </span>
            <h2 className="cs-title">Pendiente por implementar</h2>
            <p className="cs-sub">
              {feature ? (
                <>
                  <strong>{feature}</strong> estará disponible pronto.
                </>
              ) : (
                'Esta función estará disponible pronto.'
              )}
            </p>
            <button type="button" className="cs-btn" onClick={hide}>
              Entendido
            </button>
          </div>
        </div>
      )}
    </ComingSoonContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useComingSoon(): ComingSoonValue {
  const ctx = useContext(ComingSoonContext);
  if (!ctx) throw new Error('useComingSoon debe usarse dentro de <ComingSoonProvider>');
  return ctx;
}
