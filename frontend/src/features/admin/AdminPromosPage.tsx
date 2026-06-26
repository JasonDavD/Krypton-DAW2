import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { isAxiosError } from 'axios';
import { Plus } from 'lucide-react';
import { createPromo, listPromos } from './promos.api';
import type { PromoResponse } from '../../models/promo';
import './admin.css';

/** Muestra el valor del cupón según su tipo: "10%" (porcentaje) o "S/ 10" (monto). */
function formatValue(p: PromoResponse): string {
  return p.type === 'PORCENTAJE' ? `${p.value}%` : `S/ ${p.value}`;
}

/** Sección de cupones del panel admin: lista + alta. */
export function AdminPromosPage() {
  const [promos, setPromos] = useState<PromoResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Form de alta.
  const [code, setCode] = useState('');
  const [type, setType] = useState<'PORCENTAJE' | 'MONTO'>('PORCENTAJE');
  const [value, setValue] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  const reload = useCallback(() => {
    setLoading(true);
    listPromos()
      .then(setPromos)
      .catch(() => setError('No se pudieron cargar los cupones.'))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { reload(); }, [reload]);

  const canSubmit = code.trim() !== '' && Number(value) > 0;

  const onCreate = async (e: FormEvent) => {
    e.preventDefault();
    if (!canSubmit) return;
    setSubmitting(true);
    setCreateError(null);
    try {
      await createPromo({ code: code.trim(), type, value: Number(value) });
      setCode('');
      setType('PORCENTAJE');
      setValue('');
      reload();
    } catch (err) {
      // 409 si el código ya existe → mensaje específico; el resto, fallback genérico.
      if (isAxiosError(err) && err.response?.status === 409) {
        setCreateError('Ya existe un cupón con ese código');
      } else {
        setCreateError('No se pudo crear el cupón.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="adm">
      <header className="adm-head">
        <div>
          <h1>Cupones</h1>
          <p className="adm-sub">{loading ? 'Cargando…' : `${promos.length} ${promos.length === 1 ? 'cupón' : 'cupones'}`}</p>
        </div>
      </header>

      <form className="adm-create" onSubmit={onCreate}>
        <div className="adm-create__row">
          <label className="adm-field">
            <span>Código</span>
            <input value={code} onChange={(e) => setCode(e.target.value)} placeholder="VERANO10" />
          </label>
          <label className="adm-field">
            <span>Tipo</span>
            <select value={type} onChange={(e) => setType(e.target.value as 'PORCENTAJE' | 'MONTO')}>
              <option value="PORCENTAJE">Porcentaje</option>
              <option value="MONTO">Monto</option>
            </select>
          </label>
          <label className="adm-field">
            <span>Valor</span>
            <input
              type="number"
              min="0"
              step="any"
              value={value}
              onChange={(e) => setValue(e.target.value)}
              placeholder={type === 'PORCENTAJE' ? '10' : '20'}
            />
          </label>
          <button type="submit" className="adm-btn" disabled={submitting || !canSubmit}>
            <Plus size={18} /> {submitting ? 'Creando…' : 'Crear cupón'}
          </button>
        </div>
        {createError && <p className="adm-alert adm-create__err">{createError}</p>}
      </form>

      {error && <p className="adm-alert">{error}</p>}

      <div className="adm-tablewrap">
        <table className="adm-table">
          <thead>
            <tr><th>Código</th><th>Tipo</th><th>Valor</th><th>Activo</th></tr>
          </thead>
          <tbody>
            {promos.map((p) => (
              <tr key={p.id}>
                <td className="adm-name">{p.code}</td>
                <td>{p.type}</td>
                <td>{formatValue(p)}</td>
                <td>
                  <span className={p.active ? 'adm-ubadge adm-ubadge--on' : 'adm-ubadge adm-ubadge--off'}>
                    {p.active ? 'Activo' : 'Inactivo'}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {!loading && promos.length === 0 && <p className="adm-empty">No hay cupones.</p>}
      </div>
    </div>
  );
}
