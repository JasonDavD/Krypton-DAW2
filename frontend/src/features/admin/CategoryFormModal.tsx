import { useState, type FormEvent } from 'react';
import { X } from 'lucide-react';
import { createCategory, updateCategory } from './admin-categories.api';
import { apiErrorMessage } from '../../lib/apiError';
import type { CategoryResponse } from '../../models/product';

/** Modal de alta/edición de categoría. category=null → alta; si no, edición. */
export function CategoryFormModal({ category, onClose, onSaved }: {
  category: CategoryResponse | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const isEdit = category !== null;
  const [name, setName] = useState(category?.name ?? '');
  const [description, setDescription] = useState(category?.description ?? '');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const valid = name.trim() !== '';

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!valid || saving) return;
    setSaving(true);
    setError(null);
    const body = { name: name.trim(), description: description.trim() || null };
    try {
      if (isEdit) await updateCategory(category.id, body);
      else await createCategory(body);
      onSaved();
    } catch (err) {
      setError(apiErrorMessage(err, 'No se pudo guardar. Verificá que el nombre no esté repetido.'));
      setSaving(false);
    }
  };

  return (
    <div className="adm-modal" role="dialog" aria-modal="true" onClick={onClose}>
      <div className="adm-modal__panel" onClick={(ev) => ev.stopPropagation()}>
        <header className="adm-modal__head">
          <h2>{isEdit ? 'Editar categoría' : 'Nueva categoría'}</h2>
          <button type="button" onClick={onClose} aria-label="Cerrar"><X size={20} /></button>
        </header>

        {error && <p className="adm-alert">{error}</p>}

        <form className="adm-form" onSubmit={submit}>
          <div className="adm-form__grid">
            <label className="adm-field adm-field--full">
              <span>Nombre</span>
              <input value={name} maxLength={80} onChange={(e) => setName(e.target.value)} autoFocus />
            </label>
            <label className="adm-field adm-field--full">
              <span>Descripción (opcional)</span>
              <textarea value={description} maxLength={255} rows={3} onChange={(e) => setDescription(e.target.value)} />
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
