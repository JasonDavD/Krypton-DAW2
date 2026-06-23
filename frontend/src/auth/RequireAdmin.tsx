import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from './AuthContext';

/**
 * Guard de rutas de administración. Sin sesión → al login; con sesión pero sin rol
 * ADMIN → al inicio. El backend igual exige hasRole(ADMIN) en /api/admin/**; este
 * guard sólo evita mostrarle la UI de admin a quien no corresponde.
 */
export function RequireAdmin() {
  const { isAuthenticated, isAdmin } = useAuth();
  if (!isAuthenticated) return <Navigate to="/cuenta/ingresar" replace />;
  if (!isAdmin) return <Navigate to="/" replace />;
  return <Outlet />;
}
