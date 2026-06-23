import { useCallback, useEffect, useRef, useState } from 'react';
import { ArrowDown, ArrowUp, Star, Trash2, Upload, X } from 'lucide-react';
import { getById } from '../catalog/products.api';
import { deleteImage, reorderImages, setCover, uploadImage } from './admin-products.api';
import type { ProductImageResponse, ProductResponse } from '../../models/product';

/** Modal de galería: subir (N archivos), reordenar (↑/↓), marcar portada y borrar. */
export function ProductImagesModal({ product, onClose, onChanged }: {
  product: ProductResponse;
  onClose: () => void;
  onChanged: () => void; // refresca la tabla (imageUrl de la lista = portada)
}) {
  const [images, setImages] = useState<ProductImageResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  const load = useCallback(() => {
    setLoading(true);
    getById(product.id)
      .then((p) => setImages(p.images ?? []))
      .catch(() => setError('No se pudieron cargar las imágenes.'))
      .finally(() => setLoading(false));
  }, [product.id]);

  useEffect(() => { load(); }, [load]);

  // Envuelve cada mutación: bloquea controles, refresca galería + tabla, captura errores.
  const run = async (fn: () => Promise<void>) => {
    setBusy(true);
    setError(null);
    try {
      await fn();
      load();
      onChanged();
    } catch {
      setError('La operación falló. Revisá el archivo (imagen, máx 5MB) e intentá de nuevo.');
    } finally {
      setBusy(false);
    }
  };

  const onFiles = async (files: FileList | null) => {
    if (!files || files.length === 0) return;
    await run(async () => { for (const f of Array.from(files)) await uploadImage(product.id, f); });
    if (fileRef.current) fileRef.current.value = '';
  };

  // Reordena localmente y manda el array COMPLETO de ids (el backend lo exige).
  const move = (idx: number, dir: -1 | 1) => {
    const j = idx + dir;
    if (j < 0 || j >= images.length) return;
    const next = [...images];
    [next[idx], next[j]] = [next[j], next[idx]];
    run(() => reorderImages(product.id, next.map((im) => im.id)));
  };

  return (
    <div className="adm-modal" role="dialog" aria-modal="true" onClick={onClose}>
      <div className="adm-modal__panel" onClick={(e) => e.stopPropagation()}>
        <header className="adm-modal__head">
          <h2>Imágenes · {product.name}</h2>
          <button type="button" onClick={onClose} aria-label="Cerrar"><X size={20} /></button>
        </header>

        {error && <p className="adm-alert">{error}</p>}

        <div className="adm-up">
          <input ref={fileRef} type="file" accept="image/*" multiple hidden onChange={(e) => onFiles(e.target.files)} />
          <button type="button" className="adm-btn" disabled={busy} onClick={() => fileRef.current?.click()}>
            <Upload size={16} /> Subir imágenes
          </button>
          <span className="adm-up__hint">JPG/PNG, máx 5MB c/u</span>
        </div>

        {loading ? (
          <p className="adm-empty">Cargando…</p>
        ) : images.length === 0 ? (
          <p className="adm-empty">Este producto no tiene imágenes todavía.</p>
        ) : (
          <ul className="adm-gallery">
            {images.map((im, i) => (
              <li key={im.id} className={im.cover ? 'adm-img adm-img--cover' : 'adm-img'}>
                <img src={im.url} alt="" />
                {im.cover && <span className="adm-img__badge"><Star size={12} /> Portada</span>}
                <div className="adm-img__bar">
                  <button type="button" title="Mover arriba" disabled={busy || i === 0} onClick={() => move(i, -1)}><ArrowUp size={15} /></button>
                  <button type="button" title="Mover abajo" disabled={busy || i === images.length - 1} onClick={() => move(i, 1)}><ArrowDown size={15} /></button>
                  <button type="button" title="Marcar portada" disabled={busy || im.cover} onClick={() => run(() => setCover(product.id, im.id))}><Star size={15} /></button>
                  <button type="button" title="Borrar" className="adm-del" disabled={busy} onClick={() => run(() => deleteImage(product.id, im.id))}><Trash2 size={15} /></button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
