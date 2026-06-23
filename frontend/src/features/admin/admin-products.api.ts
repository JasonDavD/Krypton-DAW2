import { api } from '../../lib/api';
import type { ProductRequest, ProductResponse } from '../../models/product';

// ─── CRUD de productos (ADMIN) ────────────────────────────────────────────────

/** POST /api/admin/products — crea un producto (201). */
export async function createProduct(body: ProductRequest): Promise<ProductResponse> {
  const { data } = await api.post<ProductResponse>('/api/admin/products', body);
  return data;
}

/** PUT /api/admin/products/{id} — edita un producto. */
export async function updateProduct(id: number, body: ProductRequest): Promise<ProductResponse> {
  const { data } = await api.put<ProductResponse>(`/api/admin/products/${id}`, body);
  return data;
}

/** DELETE /api/admin/products/{id} — elimina (204). */
export async function deleteProduct(id: number): Promise<void> {
  await api.delete(`/api/admin/products/${id}`);
}

// ─── Galería de imágenes (ADMIN) ──────────────────────────────────────────────

/** POST .../images — sube UN archivo (multipart). Axios setea el Content-Type. */
export async function uploadImage(productId: number, file: File): Promise<void> {
  const form = new FormData();
  form.append('file', file);
  await api.post(`/api/admin/products/${productId}/images`, form);
}

/** DELETE .../images/{imageId} — quita una imagen (204). */
export async function deleteImage(productId: number, imageId: number): Promise<void> {
  await api.delete(`/api/admin/products/${productId}/images/${imageId}`);
}

/** PATCH .../images/reorder — body = TODOS los ids del producto en el nuevo orden. */
export async function reorderImages(productId: number, orderedIds: number[]): Promise<void> {
  await api.patch(`/api/admin/products/${productId}/images/reorder`, orderedIds);
}

/** PATCH .../images/{imageId}/cover — marca la portada (idempotente). */
export async function setCover(productId: number, imageId: number): Promise<void> {
  await api.patch(`/api/admin/products/${productId}/images/${imageId}/cover`);
}
