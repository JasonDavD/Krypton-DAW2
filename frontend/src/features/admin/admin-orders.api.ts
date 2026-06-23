import { api } from '../../lib/api';
import { saveBlob } from '../../lib/download';
import type { OrderResponse, OrderStatus } from '../../models/order';
import type { PageResponse } from '../../models/product';

/** Filtros opcionales del listado admin. from/to: Instant ISO-8601. */
export interface OrderFilter {
  status?: OrderStatus;
  from?: string;
  to?: string;
}

/** GET /api/admin/orders — paginado, más nuevos primero, con filtros opcionales. */
export async function getAllOrders(page: number, size = 10, filter: OrderFilter = {}): Promise<PageResponse<OrderResponse>> {
  const params: Record<string, string | number> = { page, size, sort: 'orderDate,desc' };
  if (filter.status) params.status = filter.status;
  if (filter.from) params.from = filter.from;
  if (filter.to) params.to = filter.to;
  const { data } = await api.get<PageResponse<OrderResponse>>('/api/admin/orders', { params });
  return data;
}

/** GET /api/admin/orders/{id} — cualquier pedido (404 si no existe). */
export async function getAdminOrder(id: number): Promise<OrderResponse> {
  const { data } = await api.get<OrderResponse>(`/api/admin/orders/${id}`);
  return data;
}

/**
 * PUT /api/admin/orders/{id}/status — cambia el estado respetando la máquina de
 * estados (PENDIENTE→{CONFIRMADA,CANCELADA}, CONFIRMADA→{CANCELADA}). 422 si es ilegal.
 */
export async function updateOrderStatus(id: number, status: OrderStatus): Promise<OrderResponse> {
  const { data } = await api.put<OrderResponse>(`/api/admin/orders/${id}/status`, { status });
  return data;
}

/** GET /api/admin/orders/{id}/comprobante — descarga el PDF de la boleta/factura (cualquier pedido pagado). */
export async function downloadAdminComprobante(id: number): Promise<void> {
  const { data } = await api.get<Blob>(`/api/admin/orders/${id}/comprobante`, { responseType: 'blob' });
  saveBlob(data, `comprobante_${id}.pdf`);
}
