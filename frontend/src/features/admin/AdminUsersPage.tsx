import { useCallback, useEffect, useState } from 'react';
import { Plus, Search } from 'lucide-react';
import { listUsers, updateUserRole, updateUserStatus } from './admin-users.api';
import { UserFormModal } from './UserFormModal';
import { apiErrorMessage } from '../../lib/apiError';
import type { Role, UserResponse } from '../../models/auth';
import './admin.css';

const dateFmt = new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium' });

/** Sección de usuarios del panel admin: lista + alta + cambio de rol y estado. */
export function AdminUsersPage() {
  const [users, setUsers] = useState<UserResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [busy, setBusy] = useState(false);
  const [creating, setCreating] = useState(false);

  const reload = useCallback(() => {
    setLoading(true);
    listUsers()
      .then(setUsers)
      .catch(() => setError('No se pudieron cargar los usuarios.'))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { reload(); }, [reload]);

  // Operación inline (rol/estado): bloquea, recarga y captura el 422 (último admin).
  const run = async (fn: () => Promise<unknown>) => {
    setBusy(true);
    setError(null);
    try {
      await fn();
      reload();
    } catch (err) {
      setError(apiErrorMessage(err, 'No se pudo completar la operación.'));
    } finally {
      setBusy(false);
    }
  };

  // Filtro client-side: la lista viene completa.
  const q = query.trim().toLowerCase();
  const filtered = users.filter((u) => u.name.toLowerCase().includes(q) || u.email.toLowerCase().includes(q));

  return (
    <div className="adm">
      <header className="adm-head">
        <div>
          <h1>Usuarios</h1>
          <p className="adm-sub">{loading ? 'Cargando…' : `${users.length} ${users.length === 1 ? 'usuario' : 'usuarios'}`}</p>
        </div>
        <button type="button" className="adm-new" onClick={() => setCreating(true)}><Plus size={18} /> Nuevo usuario</button>
      </header>

      <div className="adm-filters">
        <div className="adm-search">
          <Search size={16} />
          <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Buscar por nombre o email…" />
        </div>
      </div>

      {error && <p className="adm-alert">{error}</p>}

      <div className="adm-tablewrap">
        <table className="adm-table">
          <thead>
            <tr><th>Nombre</th><th>Email</th><th>Rol</th><th>Estado</th><th>Registro</th><th aria-label="Acciones"></th></tr>
          </thead>
          <tbody>
            {filtered.map((u) => (
              <tr key={u.id}>
                <td className="adm-name">{u.name}</td>
                <td>{u.email}</td>
                <td>
                  <select className="adm-role" value={u.role} disabled={busy}
                    onChange={(e) => run(() => updateUserRole(u.id, e.target.value as Role))}>
                    <option value="CLIENTE">Cliente</option>
                    <option value="ADMIN">Administrador</option>
                  </select>
                </td>
                <td><span className={u.active ? 'adm-ubadge adm-ubadge--on' : 'adm-ubadge adm-ubadge--off'}>{u.active ? 'Activo' : 'Inactivo'}</span></td>
                <td>{dateFmt.format(new Date(u.createdAt))}</td>
                <td className="adm-actions">
                  <button type="button" className="adm-toggle" disabled={busy}
                    onClick={() => run(() => updateUserStatus(u.id, !u.active))}>
                    {u.active ? 'Desactivar' : 'Activar'}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {!loading && filtered.length === 0 && (
          <p className="adm-empty">{users.length === 0 ? 'No hay usuarios.' : 'No se encontraron usuarios.'}</p>
        )}
      </div>

      {creating && <UserFormModal onClose={() => setCreating(false)} onSaved={() => { setCreating(false); reload(); }} />}
    </div>
  );
}
