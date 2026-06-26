import { api } from '../../lib/api';
import type { CreateReviewRequest, ReviewListResponse, ReviewResponse } from '../../models/review';

/** GET /api/reviews?productId={id} → promedio + total + listado (público). */
export async function getByProduct(productId: number): Promise<ReviewListResponse> {
  const { data } = await api.get<ReviewListResponse>('/api/reviews', {
    params: { productId },
  });
  return data;
}

/** POST /api/reviews → crea la reseña (requiere login; el token lo adjunta el interceptor). */
export async function create(req: CreateReviewRequest): Promise<ReviewResponse> {
  const { data } = await api.post<ReviewResponse>('/api/reviews', req);
  return data;
}
