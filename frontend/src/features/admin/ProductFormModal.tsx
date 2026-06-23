import { useEffect, useState, type FormEvent } from 'react';
import { X } from 'lucide-react';
import { listCategories } from '../catalog/products.api';
import { createProduct, updateProduct } from './admin-products.api';
import type { CategoryResponse, ProductResponse } from '../../models/product';

/** Modal de alta/edición de producto. product=null → alta; product=ProductResponse → edición. */
export function ProductFormModal({ product, onClose, onSaved }: {
  product: ProductResponse | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const isEdit = product !== null;
  const [categories, setCategories] = useState<CategoryResponse[]>([]);
  const [sku, setSku] = useState(product?.sku ?? '');
  const [name, setName] = useState(product?.name ?? '');
  const [description, setDescription] = useState(product?.description ?? '');
  const [price, setPrice] = useState(product ? String(product.price) : '');
  const [stock, setStock] = useState(product ? String(product.stock) : '');
  const [imageUrl, setImageUrl] = useState(product?.imageUrl ?? '');
  const [categoryId, setCategoryId] = useState<number | ''>(product?.categoryId ?? '');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => { listCategories().then(setCategories).catch(() => {}); }, []);

  const valid =
    sku.trim() !== '' && name.trim() !== '' &&
    price !== '' && Number(price) >= 0 &&
    stock !== '' && Number.isInteger(Number(stock)) && Number(stock) >= 0 &&
    categoryId !== '';

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!valid || saving) return;
    setSaving(true);
    setError(null);
    const body = {
      sku: sku.trim(),
      name: name.trim(),
      description: description.trim() || null,
      price: Number(price),
      stock: Number(stock),
      imageUrl: imageUrl.trim() || null,
      categoryId: Number(categoryId),
    };
    try {
      if (isEdit) await updateProduct(product.id, body);
      else await createProduct(body);
      onSaved();
    } catch {
      setError('No se pudo guardar. Verificá que el SKU no esté repetido y que los datos sean válidos.');
      setSaving(false);
    }
  };

  return (
    <div className="adm-modal" role="dialog" aria-modal="true" onClick={onClose}>
      <div className="adm-modal__panel" onClick={(e) => e.stopPropagation()}>
        <header className="adm-modal__head">
          <h2>{isEdit ? 'Editar producto' : 'Nuevo producto'}</h2>
          <button type="button" onClick={onClose} aria-label="Cerrar"><X size={20} /></button>
        </header>

        {error && <p className="adm-alert">{error}</p>}

        <form className="adm-form" onSubmit={submit}>
          <div className="adm-form__grid">
            <label className="adm-field">
              <span>SKU</span>
              <input value={sku} maxLength={60} onChange={(e) => setSku(e.target.value)} />
            </label>
            <label className="adm-field">
              <span>Categoría</span>
              <select value={categoryId} onChange={(e) => setCategoryId(e.target.value ? Number(e.target.value) : '')}>
                <option value="">Elegí una…</option>
                {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </label>
            <label className="adm-field adm-field--full">
              <span>Nombre</span>
              <input value={name} maxLength={150} onChange={(e) => setName(e.target.value)} />
            </label>
            <label className="adm-field">
              <span>Precio (S/)</span>
              <input type="number" min="0" step="0.01" value={price} onChange={(e) => setPrice(e.target.value)} />
            </label>
            <label className="adm-field">
              <span>Stock</span>
              <input type="number" min="0" step="1" value={stock} onChange={(e) => setStock(e.target.value)} />
            </label>
            <label className="adm-field adm-field--full">
              <span>URL de imagen (opcional)</span>
              <input value={imageUrl} maxLength={500} onChange={(e) => setImageUrl(e.target.value)} placeholder="o subila desde el botón Imágenes" />
            </label>
            <label className="adm-field adm-field--full">
              <span>Descripción (opcional)</span>
              <textarea value={description} maxLength={2000} rows={3} onChange={(e) => setDescription(e.target.value)} />
            </label>
          </div>

          <div className="adm-modal__foot">
            <button type="button" className="adm-btn-ghost" onClick={onClose}>Cancelar</button>
            <button type="submit" className="adm-btn" disabled={!valid || saving}>{saving ? 'Guardando…' : 'Guardar'}</button>
          </div>
        </form>
      </div>
    </div>
  );
}
