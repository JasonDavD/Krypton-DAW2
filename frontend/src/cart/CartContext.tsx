import {
  createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode,
} from 'react';
import { useAuth } from '../auth/AuthContext';
import * as cartApi from '../features/cart/cart.api';
import type { CartItemRequest, CartResponse } from '../models/cart';

/**
 * Estado global del carrito (única fuente de verdad). El backend lo persiste por
 * usuario, así que cargamos al autenticar y limpiamos al cerrar sesión. add/update
 * devuelven el carrito completo (lo seteamos directo); remove/clear son 204 → refetch.
 */
interface CartContextValue {
  cart: CartResponse | null;
  itemCount: number;
  loading: boolean;
  addItem: (body: CartItemRequest) => Promise<void>;
  updateItem: (itemId: number, quantity: number) => Promise<void>;
  removeItem: (itemId: number) => Promise<void>;
  clear: () => Promise<void>;
  refresh: () => Promise<void>;
}

const CartContext = createContext<CartContextValue | undefined>(undefined);

export function CartProvider({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuth();
  const [cart, setCart] = useState<CartResponse | null>(null);
  const [loading, setLoading] = useState(false);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      setCart(await cartApi.getCart());
    } catch {
      setCart(null);
    } finally {
      setLoading(false);
    }
  }, []);

  // Cargar el carrito al autenticarse; limpiarlo al cerrar sesión.
  useEffect(() => {
    if (isAuthenticated) void refresh();
    else setCart(null);
  }, [isAuthenticated, refresh]);

  const addItem = useCallback(async (body: CartItemRequest) => {
    setCart(await cartApi.addItem(body));
  }, []);

  const updateItem = useCallback(async (itemId: number, quantity: number) => {
    setCart(await cartApi.updateItem(itemId, quantity));
  }, []);

  const removeItem = useCallback(async (itemId: number) => {
    await cartApi.removeItem(itemId);
    await refresh();
  }, [refresh]);

  const clear = useCallback(async () => {
    await cartApi.clearCart();
    setCart(null);
  }, []);

  const itemCount = useMemo(
    () => cart?.items.reduce((sum, it) => sum + it.quantity, 0) ?? 0,
    [cart],
  );

  const value = useMemo<CartContextValue>(
    () => ({ cart, itemCount, loading, addItem, updateItem, removeItem, clear, refresh }),
    [cart, itemCount, loading, addItem, updateItem, removeItem, clear, refresh],
  );

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export function useCart(): CartContextValue {
  const ctx = useContext(CartContext);
  if (!ctx) throw new Error('useCart debe usarse dentro de <CartProvider>');
  return ctx;
}
