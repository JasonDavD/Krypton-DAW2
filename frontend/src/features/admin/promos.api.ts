import { api } from '../../lib/api';
import type { CreatePromoRequest, PromoResponse } from '../../models/promo';

/** GET /api/admin/promos — lista todos los cupones. */
export async function listPromos(): Promise<PromoResponse[]> {
  const { data } = await api.get<PromoResponse[]>('/api/admin/promos');
  return data;
}

/** POST /api/admin/promos — crea un cupón (201). 409 si el código ya existe. */
export async function createPromo(req: CreatePromoRequest): Promise<PromoResponse> {
  const { data } = await api.post<PromoResponse>('/api/admin/promos', req);
  return data;
}
