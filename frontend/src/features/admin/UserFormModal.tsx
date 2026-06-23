import { useState, type FormEvent } from 'react';
import { X } from 'lucide-react';
import { createUser } from './admin-users.api';
import { apiErrorMessage } from '../../lib/apiError';
import type { Role } from '../../models/auth';

/** Modal de alta de usuario por el admin (el rol es elegible: CLIENTE o ADMIN). */
export function UserFormModal({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) {
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState<Role>('CLIENTE');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const valid = name.trim() !== '' && /.+@.+\..+/.test(email) && password.length >= 8;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!valid || saving) return;
    setSaving(true);
    setError(null);
    try {
      await createUser({ name: name.trim(), email: email.trim(), password, role });
      onSaved();
    } catch (err) {
      setError(apiErrorMessage(err, 'No se pudo crear. Verificá que el email no esté registrado.'));
      setSaving(false);
    }
  };

  return (
    <div className="adm-modal" role="dialog" aria-modal="true" onClick={onClose}>
      <div className="adm-modal__panel" onClick={(ev) => ev.stopPropagation()}>
        <header className="adm-modal__head">
          <h2>Nuevo usuario</h2>
          <button type="button" onClick={onClose} aria-label="Cerrar"><X size={20} /></button>
        </header>

        {error && <p className="adm-alert">{error}</p>}

        <form className="adm-form" onSubmit={submit}>
          <div className="adm-form__grid">
            <label className="adm-field adm-field--full">
              <span>Nombre</span>
              <input value={name} maxLength={150} onChange={(e) => setName(e.target.value)} autoFocus />
            </label>
            <label className="adm-field adm-field--full">
              <span>Email</span>
              <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="usuario@correo.com" />
            </label>
            <label className="adm-field">
              <span>Contraseña</span>
              <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="mín. 8 caracteres" />
            </label>
            <label className="adm-field">
              <span>Rol</span>
              <select value={role} onChange={(e) => setRole(e.target.value as Role)}>
                <option value="CLIENTE">Cliente</option>
                <option value="ADMIN">Administrador</option>
              </select>
            </label>
          </div>
          <div className="adm-modal__foot">
            <button type="button" className="adm-btn-ghost" onClick={onClose}>Cancelar</button>
            <button type="submit" className="adm-btn" disabled={!valid || saving}>{saving ? 'Creando…' : 'Crear usuario'}</button>
          </div>
        </form>
      </div>
    </div>
  );
}
