/**
 * Tipos del contrato de reseñas (review-service vía gateway). Reflejan los DTOs
 * del backend (Jackson camelCase). Identificadores en inglés (contrato JSON).
 */

/** Una reseña tal como la devuelve el backend. */
export interface ReviewResponse {
  id: number;
  userEmail: string;
  rating: number;
  comment: string | null;
  createdAt: string;
}

/** Respuesta de GET /api/reviews?productId=… : agregado + listado. */
export interface ReviewListResponse {
  promedio: number;
  total: number;
  reviews: ReviewResponse[];
}

/** Body para crear una reseña (POST /api/reviews). */
export interface CreateReviewRequest {
  productId: number;
  rating: number;
  comment: string;
}
