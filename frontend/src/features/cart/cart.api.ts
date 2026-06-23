import { api } from '../../lib/api';
import type { CartItemRequest, CartResponse } from '../../models/cart';

/** GET /api/cart — carrito del usuario autenticado. */
export async function getCart(): Promise<CartResponse> {
  const { data } = await api.get<CartResponse>('/api/cart');
  return data;
}

/** POST /api/cart/items — agrega un producto (o suma cantidad si ya está). */
export async function addItem(body: CartItemRequest): Promise<CartResponse> {
  const { data } = await api.post<CartResponse>('/api/cart/items', body);
  return data;
}

/** PUT /api/cart/items/{itemId} — fija la cantidad de una línea. */
export async function updateItem(itemId: number, quantity: number): Promise<CartResponse> {
  const { data } = await api.put<CartResponse>(`/api/cart/items/${itemId}`, { quantity });
  return data;
}

/** DELETE /api/cart/items/{itemId} — quita una línea (204, sin cuerpo). */
export async function removeItem(itemId: number): Promise<void> {
  await api.delete(`/api/cart/items/${itemId}`);
}

/** DELETE /api/cart — vacía el carrito (204, sin cuerpo). */
export async function clearCart(): Promise<void> {
  await api.delete('/api/cart');
}
