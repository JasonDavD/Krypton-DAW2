import { useCallback, useEffect, useState } from 'react';
import { Bar, BarChart, CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { Download } from 'lucide-react';
import { downloadReport, getKardex, getTopProductos, getVentas } from './admin-reports.api';
import { ProductSearchSelect } from '../../components/ProductSearchSelect';
import type { KardexReport, TopProductosReport, VentasPorPeriodoReport } from '../../models/report';
import type { ProductResponse } from '../../models/product';
import './report.css';

const pen = new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN', minimumFractionDigits: 2 });
const dateTimeFmt = new Intl.DateTimeFormat('es-PE', { dateStyle: 'short', timeStyle: 'short' });
const fmtDate = (d: Date) => d.toISOString().slice(0, 10);

/** Rango por defecto: últimos 30 días. */
function defaultRange(): { desde: string; hasta: string } {
  const hasta = new Date();
  const desde = new Date();
  desde.setDate(hasta.getDate() - 30);
  return { desde: fmtDate(desde), hasta: fmtDate(hasta) };
}

/** Dashboard de reportes: KPIs + gráficos de ventas y productos, con export. */
export function AdminReportsPage() {
  const init = defaultRange();
  const [desde, setDesde] = useState(init.desde);
  const [hasta, setHasta] = useState(init.hasta);
  const [granularidad, setGranularidad] = useState<'dia' | 'mes'>('dia');
  const [ventas, setVentas] = useState<VentasPorPeriodoReport | null>(null);
  const [top, setTop] = useState<TopProductosReport | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  // Kardex: producto elegido (autocomplete) + sus movimientos en el período.
  const [kardexProduct, setKardexProduct] = useState<ProductResponse | null>(null);
  const [kardex, setKardex] = useState<KardexReport | null>(null);
  const [kardexLoading, setKardexLoading] = useState(false);

  const reload = useCallback(() => {
    setLoading(true);
    setError(false);
    Promise.all([getVentas(desde, hasta, granularidad), getTopProductos(desde, hasta, 10)])
      .then(([v, t]) => { setVentas(v); setTop(t); })
      .catch(() => setError(true))
      .finally(() => setLoading(false));
  }, [desde, hasta, granularidad]);

  useEffect(() => { reload(); }, [reload]);

  // Kardex del producto elegido, dentro del período.
  useEffect(() => {
    if (!kardexProduct) { setKardex(null); return; }
    setKardexLoading(true);
    getKardex(kardexProduct.id, desde, hasta)
      .then(setKardex)
      .catch(() => setKardex(null))
      .finally(() => setKardexLoading(false));
  }, [kardexProduct, desde, hasta]);

  return (
    <div className="adm">
      <header className="adm-head">
        <div><h1>Reportes</h1><p className="adm-sub">Ventas y productos del período seleccionado</p></div>
      </header>

      <div className="adm-filters">
        <label className="adm-date">Desde <input type="date" value={desde} onChange={(e) => setDesde(e.target.value)} /></label>
        <label className="adm-date">Hasta <input type="date" value={hasta} onChange={(e) => setHasta(e.target.value)} /></label>
        <select className="adm-filter-sel" value={granularidad} onChange={(e) => setGranularidad(e.target.value as 'dia' | 'mes')}>
          <option value="dia">Por día</option>
          <option value="mes">Por mes</option>
        </select>
      </div>

      {error && <p className="adm-alert">No se pudieron cargar los reportes.</p>}

      {loading ? (
        <p className="adm-empty">Cargando reportes…</p>
      ) : (
        <>
          <div className="rep-kpis">
            <div className="rep-kpi"><span className="rep-kpi__label">Total facturado</span><span className="rep-kpi__value">{pen.format(ventas?.totalFacturado ?? 0)}</span></div>
            <div className="rep-kpi"><span className="rep-kpi__label">Órdenes</span><span className="rep-kpi__value">{ventas?.totalOrdenes ?? 0}</span></div>
            <div className="rep-kpi"><span className="rep-kpi__label">Ticket promedio</span><span className="rep-kpi__value">{pen.format(ventas?.ticketPromedio ?? 0)}</span></div>
          </div>

          <div className="rep-card">
            <div className="rep-card__head">
              <h2>Ventas por {granularidad === 'mes' ? 'mes' : 'día'}</h2>
              <div className="rep-dl">
                <button type="button" onClick={() => downloadReport('/api/admin/reports/ventas/excel', { desde, hasta, granularidad }, `ventas_${desde}_${hasta}.xlsx`)}><Download size={15} /> Excel</button>
                <button type="button" onClick={() => downloadReport('/api/admin/reports/ventas/pdf', { desde, hasta, granularidad }, `ventas_${desde}_${hasta}.pdf`)}><Download size={15} /> PDF</button>
              </div>
            </div>
            {ventas && ventas.filas.length > 0 ? (
              <ResponsiveContainer width="100%" height={280}>
                <LineChart data={ventas.filas} margin={{ top: 8, right: 16, left: 8, bottom: 4 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#eef0f3" />
                  <XAxis dataKey="periodo" tick={{ fontSize: 12 }} />
                  <YAxis tick={{ fontSize: 12 }} />
                  <Tooltip formatter={(value) => pen.format(Number(value))} />
                  <Line type="monotone" dataKey="monto" name="Monto" stroke="#F37402" strokeWidth={2.5} dot={{ r: 3 }} />
                </LineChart>
              </ResponsiveContainer>
            ) : <p className="adm-empty">Sin ventas en el período.</p>}
          </div>

          <div className="rep-card">
            <div className="rep-card__head">
              <h2>Productos más vendidos</h2>
              <div className="rep-dl">
                <button type="button" onClick={() => downloadReport('/api/admin/reports/productos-vendidos/excel', { desde, hasta, limit: 10 }, `productos_${desde}_${hasta}.xlsx`)}><Download size={15} /> Excel</button>
                <button type="button" onClick={() => downloadReport('/api/admin/reports/productos-vendidos/pdf', { desde, hasta, limit: 10 }, `productos_${desde}_${hasta}.pdf`)}><Download size={15} /> PDF</button>
              </div>
            </div>
            {top && top.productos.length > 0 ? (
              <ResponsiveContainer width="100%" height={300}>
                <BarChart data={top.productos} margin={{ top: 8, right: 16, left: 8, bottom: 40 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#eef0f3" />
                  <XAxis dataKey="nombre" tick={{ fontSize: 11 }} interval={0} angle={-20} textAnchor="end" height={60} />
                  <YAxis tick={{ fontSize: 12 }} allowDecimals={false} />
                  <Tooltip />
                  <Bar dataKey="unidades" name="Unidades" fill="#1d5fd0" radius={[5, 5, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            ) : <p className="adm-empty">Sin productos vendidos en el período.</p>}
          </div>

          {/* Kardex por producto */}
          <div className="rep-card">
            <div className="rep-card__head">
              <h2>Kardex por producto</h2>
              {kardexProduct && (
                <div className="rep-dl">
                  <button type="button" onClick={() => downloadReport('/api/admin/reports/kardex/excel', { productId: kardexProduct.id, desde, hasta }, `kardex_${kardexProduct.id}.xlsx`)}><Download size={15} /> Excel</button>
                  <button type="button" onClick={() => downloadReport('/api/admin/reports/kardex/pdf', { productId: kardexProduct.id, desde, hasta }, `kardex_${kardexProduct.id}.pdf`)}><Download size={15} /> PDF</button>
                </div>
              )}
            </div>
            <div className="rep-kx-sel">
              <ProductSearchSelect value={kardexProduct} onChange={setKardexProduct} placeholder="Buscá un producto…" />
            </div>
            {!kardexProduct ? (
              <p className="adm-empty">Buscá y elegí un producto para ver sus movimientos de stock.</p>
            ) : kardexLoading ? (
              <p className="adm-empty">Cargando…</p>
            ) : !kardex || kardex.movimientos.length === 0 ? (
              <p className="adm-empty">Sin movimientos en el período.</p>
            ) : (
              <>
                <p className="rep-stock">Stock actual: <strong>{kardex.stockActual}</strong></p>
                <div className="adm-tablewrap">
                  <table className="adm-table">
                    <thead><tr><th>Fecha</th><th>Tipo</th><th>Cantidad</th><th>Motivo</th></tr></thead>
                    <tbody>
                      {kardex.movimientos.map((m, i) => (
                        <tr key={i}>
                          <td>{dateTimeFmt.format(new Date(m.fecha))}</td>
                          <td><span className={m.tipo === 'ENTRADA' ? 'rep-mov rep-mov--entrada' : 'rep-mov rep-mov--salida'}>{m.tipo}</span></td>
                          <td>{m.cantidad}</td>
                          <td>{m.reason}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </>
            )}
          </div>
        </>
      )}
    </div>
  );
}
