import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';

/**
 * Lleva el scroll al tope al cambiar de RUTA (pathname). React Router no lo hace
 * solo: sin esto, navegar desde una página scrolleada deja la nueva vista con el
 * scroll heredado (p.ej. de la home a /catalogo se ve el footer).
 *
 * Depende sólo del pathname (no del query string), así cambiar filtros dentro del
 * catálogo (?categoryId=…) NO salta arriba; sólo al navegar a otra vista.
 */
export function ScrollToTop() {
  const { pathname } = useLocation();
  useEffect(() => { window.scrollTo(0, 0); }, [pathname]);
  return null;
}
