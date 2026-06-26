/**
 * Tipos del contrato de promociones/cupones. Reflejan los DTOs del backend
 * (Jackson camelCase). El descuento real lo calcula y aplica el BACKEND —
 * el front sólo lo previsualiza. Identificadores en inglés (contrato JSON).
 */

/** Body de POST /api/promos/apply — valida un cupón sobre un monto. */
export interface ApplyPromoRequest {
  code: string;
  amount: number;
}

/** Respuesta de POST /api/promos/apply — descuento y monto final ya con cupón. */
export interface DiscountResponse {
  code: string;
  discount: number;
  finalAmount: number;
}
