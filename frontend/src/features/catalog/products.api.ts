import { api } from '../../lib/api';
import type { CategoryResponse, PageResponse, ProductResponse, CatalogFilter } from '../../models/product';

/** GET /api/products?size=limit → solo el contenido de la primera página (para destacados). */
export async function featured(limit = 8): Promise<ProductResponse[]> {
  const { data } = await api.get<PageResponse<ProductResponse>>('/api/products', {
    params: { page: 0, size: limit },
  });
  return data.content;
}

/** GET /api/products con filtro + paginación (para el catálogo). page es base-cero. */
export async function search(filter: CatalogFilter, page: number, size = 12): Promise<PageResponse<ProductResponse>> {
  const params: Record<string, string | number> = { page, size };
  if (filter.name?.trim()) params.name = filter.name.trim();
  if (filter.categoryId != null) params.categoryId = filter.categoryId;
  if (filter.priceMin != null) params.priceMin = filter.priceMin;
  if (filter.priceMax != null) params.priceMax = filter.priceMax;
  const { data } = await api.get<PageResponse<ProductResponse>>('/api/products', { params });
  return data;
}

/** GET /api/products/{id} */
export async function getById(id: number): Promise<ProductResponse> {
  const { data } = await api.get<ProductResponse>(`/api/products/${id}`);
  return data;
}

/** GET /api/categories */
export async function listCategories(): Promise<CategoryResponse[]> {
  const { data } = await api.get<CategoryResponse[]>('/api/categories');
  return data;
}
