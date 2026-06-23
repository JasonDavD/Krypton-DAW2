import { NavLink, Outlet } from 'react-router-dom';
import { BarChart3, ClipboardList, Package, Tags, Users } from 'lucide-react';
import './admin-layout.css';

/** Secciones del panel. Las que aún no tienen vista montan un Placeholder (ruta lista). */
const SECTIONS = [
  { to: '/admin/productos', label: 'Productos', icon: Package },
  { to: '/admin/categorias', label: 'Categorías', icon: Tags },
  { to: '/admin/usuarios', label: 'Usuarios', icon: Users },
  { to: '/admin/pedidos', label: 'Pedidos', icon: ClipboardList },
  { to: '/admin/reportes', label: 'Reportes', icon: BarChart3 },
];

/**
 * Shell del panel de administración: sidebar de secciones + área de contenido (Outlet).
 * Va dentro de MainLayout (navbar/footer) y de RequireAdmin (sólo ADMIN).
 */
export function AdminLayout() {
  return (
    <div className="adl">
      <aside className="adl-side">
        <div className="adl-side__title">Administración</div>
        <nav className="adl-nav">
          {SECTIONS.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) => (isActive ? 'adl-link adl-link--on' : 'adl-link')}
            >
              <Icon size={18} />
              <span>{label}</span>
            </NavLink>
          ))}
        </nav>
      </aside>
      <div className="adl-content">
        <Outlet />
      </div>
    </div>
  );
}
