import { api } from '../../lib/api';
import type { ApplyPromoRequest, DiscountResponse } from '../../models/promo';

/**
 * POST /api/promos/apply — valida el cupón sobre el monto y devuelve el
 * descuento. Requiere sesión. Lanza 422 si el código es inválido/inactivo.
 */
export async function applyPromo(req: ApplyPromoRequest): Promise<DiscountResponse> {
  const { data } = await api.post<DiscountResponse>('/api/promos/apply', req);
  return data;
}
