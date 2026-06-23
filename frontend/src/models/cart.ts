/**
 * Tipos del contrato del carrito. Reflejan los DTOs del backend (Jackson
 * camelCase). Identificadores en inglés (contrato JSON).
 */

/** Línea del carrito. price/subtotal: Java BigDecimal → number. */
export interface CartItemResponse {
  itemId: number;
  productId: number;
  productName: string;
  sku: string;
  price: number;
  quantity: number;
  subtotal: number;
}

export interface CartResponse {
  /** null cuando el carrito aún no existe (usuario sin carrito persistido). */
  cartId: number | null;
  items: CartItemResponse[];
  /** Total del carrito (BigDecimal → number). */
  total: number;
  /** Instant ISO-8601; null cuando el carrito está vacío / no existe. */
  updatedAt: string | null;
}

/** Body para agregar un ítem (POST /api/cart/items). quantity ≥ 1. */
export interface CartItemRequest {
  productId: number;
  quantity: number;
}
