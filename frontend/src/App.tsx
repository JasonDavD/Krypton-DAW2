import { Navigate, Route, Routes } from 'react-router-dom';
import { ScrollToTop } from './components/ScrollToTop';
import { MainLayout } from './app/MainLayout';
import { HomePage } from './features/home/HomePage';
import { LoginPage } from './features/auth/LoginPage';
import { RegisterPage } from './features/auth/RegisterPage';
import { CatalogPage } from './features/catalog/CatalogPage';
import { ProductDetailPage } from './features/catalog/ProductDetailPage';
import { CartPage } from './features/cart/CartPage';
import { CheckoutPage } from './features/checkout/CheckoutPage';
import { OrdersPage } from './features/orders/OrdersPage';
import { OrderDetailPage } from './features/orders/OrderDetailPage';
import { TermsPage } from './features/legal/TermsPage';
import { PrivacyPage } from './features/legal/PrivacyPage';
import { RequireAdmin } from './auth/RequireAdmin';
import { AdminLayout } from './features/admin/AdminLayout';
import { AdminProductsPage } from './features/admin/AdminProductsPage';
import { AdminCategoriesPage } from './features/admin/AdminCategoriesPage';
import { AdminOrdersPage } from './features/admin/AdminOrdersPage';
import { AdminUsersPage } from './features/admin/AdminUsersPage';
import { AdminReportsPage } from './features/admin/AdminReportsPage';

/**
 * Mapa de rutas. Dos shells:
 *  - /cuenta/** → bare (auth inmersiva, sin navbar/footer)
 *  - resto → MainLayout (navbar + footer)
 * Las vistas placeholder se reemplazan por las portadas del diseño aprobado.
 */
function App() {
  return (
    <>
      <ScrollToTop />
      <Routes>
      {/* Auth (sin chrome) */}
      <Route path="/cuenta/ingresar" element={<LoginPage />} />
      <Route path="/cuenta/registro" element={<RegisterPage />} />

      {/* Tienda (con navbar + footer) */}
      <Route element={<MainLayout />}>
        <Route path="/" element={<HomePage />} />
        <Route path="/catalogo" element={<CatalogPage />} />
        <Route path="/catalogo/:id" element={<ProductDetailPage />} />
        <Route path="/carrito" element={<CartPage />} />
        <Route path="/checkout" element={<CheckoutPage />} />
        <Route path="/pedidos" element={<OrdersPage />} />
        <Route path="/pedidos/:id" element={<OrderDetailPage />} />
        <Route path="/terminos" element={<TermsPage />} />
        <Route path="/privacidad" element={<PrivacyPage />} />
        <Route element={<RequireAdmin />}>
          <Route path="/admin" element={<AdminLayout />}>
            <Route index element={<Navigate to="/admin/productos" replace />} />
            <Route path="productos" element={<AdminProductsPage />} />
            <Route path="categorias" element={<AdminCategoriesPage />} />
            <Route path="usuarios" element={<AdminUsersPage />} />
            <Route path="pedidos" element={<AdminOrdersPage />} />
            <Route path="reportes" element={<AdminReportsPage />} />
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
      </Routes>
    </>
  );
}

export default App;
