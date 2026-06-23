import { useCallback, useEffect, useState } from 'react';
import { Pencil, Plus, Search, Trash2 } from 'lucide-react';
import { listCategories } from '../catalog/products.api';
import { deleteCategory } from './admin-categories.api';
import { CategoryFormModal } from './CategoryFormModal';
import { apiErrorMessage } from '../../lib/apiError';
import type { CategoryResponse } from '../../models/product';
import './admin.css';

/** Sección de categorías del panel admin: lista + CRUD. */
export function AdminCategoriesPage() {
  const [categories, setCategories] = useState<CategoryResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState('');

  const [editing, setEditing] = useState<CategoryResponse | 'new' | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<number | null>(null);

  const reload = useCallback(() => {
    setLoading(true);
    listCategories()
      .then(setCategories)
      .catch(() => setError('No se pudieron cargar las categorías.'))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { reload(); }, [reload]);

  const onDelete = async (id: number) => {
    setError(null);
    try {
      await deleteCategory(id);
      setConfirmDelete(null);
      reload();
    } catch (err) {
      // 409 si la categoría tiene productos asociados → mostramos el mensaje del backend.
      setError(apiErrorMessage(err, 'No se pudo borrar la categoría.'));
      setConfirmDelete(null);
    }
  };

  // Filtro client-side: las categorías vienen todas, así que filtramos en memoria.
  const filtered = categories.filter((c) => c.name.toLowerCase().includes(query.trim().toLowerCase()));

  return (
    <div className="adm">
      <header className="adm-head">
        <div>
          <h1>Categorías</h1>
          <p className="adm-sub">{loading ? 'Cargando…' : `${categories.length} ${categories.length === 1 ? 'categoría' : 'categorías'}`}</p>
        </div>
        <button type="button" className="adm-new" onClick={() => setEditing('new')}><Plus size={18} /> Nueva categoría</button>
      </header>

      <div className="adm-filters">
        <div className="adm-search">
          <Search size={16} />
          <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Buscar por nombre…" />
        </div>
      </div>

      {error && <p className="adm-alert">{error}</p>}

      <div className="adm-tablewrap">
        <table className="adm-table">
          <thead>
            <tr><th>Nombre</th><th>Descripción</th><th aria-label="Acciones"></th></tr>
          </thead>
          <tbody>
            {filtered.map((c) => (
              <tr key={c.id}>
                <td className="adm-name">{c.name}</td>
                <td>{c.description || <span className="adm-muted">—</span>}</td>
                <td className="adm-actions">
                  {confirmDelete === c.id ? (
                    <span className="adm-confirm">
                      ¿Borrar?
                      <button type="button" className="adm-confirm__yes" onClick={() => onDelete(c.id)}>Sí</button>
                      <button type="button" className="adm-confirm__no" onClick={() => setConfirmDelete(null)}>No</button>
                    </span>
                  ) : (
                    <>
                      <button type="button" title="Editar" onClick={() => setEditing(c)}><Pencil size={17} /></button>
                      <button type="button" title="Borrar" className="adm-del" onClick={() => { setError(null); setConfirmDelete(c.id); }}><Trash2 size={17} /></button>
                    </>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {!loading && filtered.length === 0 && (
          <p className="adm-empty">{categories.length === 0 ? 'No hay categorías.' : 'No se encontraron categorías.'}</p>
        )}
      </div>

      {editing && (
        <CategoryFormModal
          category={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={() => { setEditing(null); reload(); }}
        />
      )}
    </div>
  );
}
