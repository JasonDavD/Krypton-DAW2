import { Link } from 'react-router-dom';
import type { ReactNode } from 'react';
import './legal.css';

/** Chrome común de las páginas legales: migas + título + fecha + contenido. */
export function LegalDoc({ title, updated, children }: {
  title: string;
  updated: string;
  children: ReactNode;
}) {
  return (
    <div className="legal">
      <nav className="legal-crumb" aria-label="Migas">
        <Link to="/">Inicio</Link><span>/</span><strong>{title}</strong>
      </nav>
      <h1 className="legal-title">{title}</h1>
      <p className="legal-updated">Última actualización: {updated}</p>
      <div className="legal-body">{children}</div>
    </div>
  );
}
