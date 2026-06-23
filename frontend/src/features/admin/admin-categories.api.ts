import { api } from '../../lib/api';
import type { CategoryRequest, CategoryResponse } from '../../models/product';

/** POST /api/admin/categories — crea una categoría (201). */
export async function createCategory(body: CategoryRequest): Promise<CategoryResponse> {
  const { data } = await api.post<CategoryResponse>('/api/admin/categories', body);
  return data;
}

/** PUT /api/admin/categories/{id} — edita una categoría. */
export async function updateCategory(id: number, body: CategoryRequest): Promise<CategoryResponse> {
  const { data } = await api.put<CategoryResponse>(`/api/admin/categories/${id}`, body);
  return data;
}

/** DELETE /api/admin/categories/{id} — elimina (204). 409 si tiene productos asociados. */
export async function deleteCategory(id: number): Promise<void> {
  await api.delete(`/api/admin/categories/${id}`);
}
