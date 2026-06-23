/**
 * Tipos del contrato de productos y categorías. Reflejan los DTOs del backend
 * (Jackson camelCase). Identificadores en inglés (contrato JSON).
 */

export interface CategoryResponse {
  id: number;
  name: string;
  description: string | null;
}

/** Body para crear/editar una categoría (POST/PUT /api/admin/categories). */
export interface CategoryRequest {
  name: string;
  description?: string | null;
}

/** Imagen del carrusel de un producto. */
export interface ProductImageResponse {
  id: number;
  /** URL completa de servicio: base-url + /api/uploads/images/{filename} */
  url: string;
  displayOrder: number;
  cover: boolean;
}

export interface ProductResponse {
  id: number;
  sku: string;
  name: string;
  description: string | null;
  /** Java BigDecimal → number. */
  price: number;
  stock: number;
  imageUrl: string | null;
  active: boolean;
  categoryId: number;
  categoryName: string;
  /** Galería ordenada por displayOrder ASC. Ausente en endpoints de lista. */
  images?: ProductImageResponse[];
}

/** Body para crear/editar un producto (POST/PUT /api/admin/products). Refleja ProductRequest. */
export interface ProductRequest {
  sku: string;
  name: string;
  description?: string | null;
  price: number;
  stock: number;
  imageUrl?: string | null;
  categoryId: number;
}

/** Envoltorio de paginación genérico — refleja PageResponse<T> de Spring Data. */
export interface PageResponse<T> {
  content: T[];
  /** Página base-cero. */
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Estado del filtro del catálogo (búsqueda). */
export interface CatalogFilter {
  name?: string;
  categoryId?: number;
  priceMin?: number;
  priceMax?: number;
}

/** Imagen de respaldo cuando ProductResponse.imageUrl es null. */
export const PLACEHOLDER_IMAGE = '/brand/Krypton-navy.svg';
