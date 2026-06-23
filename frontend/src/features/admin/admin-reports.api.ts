import { api } from '../../lib/api';
import type { KardexReport, TopProductosReport, VentasPorPeriodoReport } from '../../models/report';

/** GET /api/admin/reports/ventas — datos de ventas por período (JSON). */
export async function getVentas(desde: string, hasta: string, granularidad: 'dia' | 'mes'): Promise<VentasPorPeriodoReport> {
  const { data } = await api.get<VentasPorPeriodoReport>('/api/admin/reports/ventas', {
    params: { desde, hasta, granularidad },
  });
  return data;
}

/** GET /api/admin/reports/productos-vendidos — top productos (JSON). */
export async function getTopProductos(desde: string, hasta: string, limit = 10): Promise<TopProductosReport> {
  const { data } = await api.get<TopProductosReport>('/api/admin/reports/productos-vendidos', {
    params: { desde, hasta, limit },
  });
  return data;
}

/** GET /api/admin/reports/kardex — movimientos de stock de un producto (JSON). */
export async function getKardex(productId: number, desde?: string, hasta?: string): Promise<KardexReport> {
  const params: Record<string, string | number> = { productId };
  if (desde) params.desde = desde;
  if (hasta) params.hasta = hasta;
  const { data } = await api.get<KardexReport>('/api/admin/reports/kardex', { params });
  return data;
}

/** Descarga un export (Excel/PDF) como archivo, respetando el Bearer del interceptor. */
export async function downloadReport(path: string, params: Record<string, string | number>, filename: string): Promise<void> {
  const res = await api.get(path, { params, responseType: 'blob' });
  const url = URL.createObjectURL(res.data as Blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}
