import { Link } from 'react-router-dom';
import { ShieldCheck } from 'lucide-react';
import { useComingSoon } from '../coming-soon/ComingSoon';
import './footer.css';

/**
 * Footer Krypton — navy con columnas. Iconos de redes en SVG inline (lucide quitó
 * los logos de marcas). Links sin página real → card "Pendiente por implementar".
 */
export function Footer() {
  const comingSoon = useComingSoon();
  const year = new Date().getFullYear();

  return (
    <footer className="ft">
      <div className="ft-top">
        <div>
          <Link to="/"><img className="ft-logo" src="/brand/Krypton-white.svg" alt="Krypton" /></Link>
          <p className="ft-blurb">Tu tienda de tecnología en el Perú. Lo último en laptops, componentes y audio con envío express.</p>
          <div className="ft-social">
            <button type="button" onClick={() => comingSoon.show('Instagram')} aria-label="Instagram">
              <svg viewBox="0 0 24 24" width="19" height="19" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round"><rect x="2" y="2" width="20" height="20" rx="5" /><circle cx="12" cy="12" r="4" /><circle cx="17.5" cy="6.5" r="1" fill="currentColor" stroke="none" /></svg>
            </button>
            <button type="button" onClick={() => comingSoon.show('Facebook')} aria-label="Facebook">
              <svg viewBox="0 0 24 24" width="19" height="19" fill="currentColor"><path d="M22 12a10 10 0 1 0-11.56 9.88v-6.99H7.9V12h2.54V9.8c0-2.5 1.49-3.89 3.78-3.89 1.09 0 2.24.2 2.24.2v2.46h-1.26c-1.24 0-1.63.77-1.63 1.56V12h2.78l-.44 2.89h-2.34v6.99A10 10 0 0 0 22 12z" /></svg>
            </button>
            <button type="button" onClick={() => comingSoon.show('YouTube')} aria-label="YouTube">
              <svg viewBox="0 0 24 24" width="19" height="19" fill="currentColor"><path d="M21.58 7.19a2.5 2.5 0 0 0-1.76-1.77C18.25 5 12 5 12 5s-6.25 0-7.82.42A2.5 2.5 0 0 0 2.42 7.2 26 26 0 0 0 2 12a26 26 0 0 0 .42 4.81 2.5 2.5 0 0 0 1.76 1.77C5.75 19 12 19 12 19s6.25 0 7.82-.42a2.5 2.5 0 0 0 1.76-1.77A26 26 0 0 0 22 12a26 26 0 0 0-.42-4.81zM10 15V9l5.2 3-5.2 3z" /></svg>
            </button>
          </div>
        </div>

        <div>
          <div className="ft-col-title">Tienda</div>
          <div className="ft-col-links">
            <Link to="/catalogo">Catálogo</Link>
            <Link to="/catalogo">Categorías</Link>
            <button type="button" onClick={() => comingSoon.show('Ofertas')}>Ofertas</button>
            <Link to="/carrito">Carrito</Link>
          </div>
        </div>

        <div>
          <div className="ft-col-title">Cuenta</div>
          <div className="ft-col-links">
            <Link to="/cuenta/ingresar">Iniciar sesión</Link>
            <Link to="/cuenta/registro">Crear cuenta</Link>
            <Link to="/pedidos">Mis pedidos</Link>
          </div>
        </div>

        <div>
          <div className="ft-col-title">Soporte</div>
          <div className="ft-col-links">
            <button type="button" onClick={() => comingSoon.show('Centro de ayuda')}>Centro de ayuda</button>
            <button type="button" onClick={() => comingSoon.show('Envíos y entregas')}>Envíos y entregas</button>
            <button type="button" onClick={() => comingSoon.show('Garantía')}>Garantía</button>
          </div>
        </div>
      </div>

      <div className="ft-bottom">
        <div className="ft-bottom-inner">
          <span className="ft-copy">© {year} Krypton E-commerce. Todos los derechos reservados.</span>
          <span className="ft-legal">
            <Link to="/terminos">Términos</Link>
            <Link to="/privacidad">Privacidad</Link>
          </span>
          <span className="ft-secure"><ShieldCheck size={16} color="var(--kr-yellow-500)" /> Pago 100% seguro</span>
        </div>
      </div>
    </footer>
  );
}
