import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Star } from 'lucide-react';
import axios from 'axios';
import { create, getByProduct } from './reviews.api';
import { useAuth } from '../../auth/AuthContext';
import type { ReviewListResponse } from '../../models/review';
import './ProductReviews.css';

const dateFmt = new Intl.DateTimeFormat('es-PE', { day: '2-digit', month: 'short', year: 'numeric' });

/** Renderiza una fila de 5 estrellas, rellenas hasta `value`. Solo lectura. */
function Stars({ value, size = 16 }: { value: number; size?: number }) {
  return (
    <span className="rv-stars" aria-hidden="true">
      {[1, 2, 3, 4, 5].map((n) => (
        <Star
          key={n}
          size={size}
          className={n <= Math.round(value) ? 'rv-star rv-star--on' : 'rv-star'}
        />
      ))}
    </span>
  );
}

/** Nombre a mostrar del autor: la parte previa al @ del email. */
function authorName(email: string): string {
  const at = email.indexOf('@');
  return at > 0 ? email.slice(0, at) : email;
}

/**
 * Reseñas de un producto: promedio + total + listado, y (si hay sesión) un
 * formulario para dejar reseña. Carga al montar y recarga tras enviar.
 */
export function ProductReviews({ productId }: { productId: number }) {
  const { isAuthenticated } = useAuth();

  const [data, setData] = useState<ReviewListResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);

  // Formulario
  const [rating, setRating] = useState(5);
  const [comment, setComment] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const load = useCallback(() => {
    setLoading(true);
    setLoadError(false);
    getByProduct(productId)
      .then(setData)
      .catch(() => setLoadError(true))
      .finally(() => setLoading(false));
  }, [productId]);

  useEffect(() => {
    load();
  }, [load]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setSubmitError(null);
    try {
      await create({ productId, rating, comment: comment.trim() });
      setComment('');
      setRating(5);
      load();
    } catch (err) {
      if (axios.isAxiosError(err) && err.response?.status === 409) {
        setSubmitError('Ya reseñaste este producto.');
      } else {
        setSubmitError('No se pudo enviar tu reseña. Intentá de nuevo.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <section className="rv" aria-label="Reseñas del producto">
      <header className="rv-head">
        <h2 className="rv-title">Reseñas</h2>
        {data && data.total > 0 && (
          <div className="rv-summary">
            <span className="rv-avg">{data.promedio.toFixed(1)}</span>
            <span className="rv-avg-max">/ 5</span>
            <Stars value={data.promedio} size={18} />
            <span className="rv-count">({data.total} {data.total === 1 ? 'reseña' : 'reseñas'})</span>
          </div>
        )}
      </header>

      {/* Formulario / aviso de login */}
      {isAuthenticated ? (
        <form className="rv-form" onSubmit={handleSubmit}>
          <div className="rv-form-row">
            <span className="rv-form-label">Tu puntuación</span>
            <div className="rv-rating-pick" role="radiogroup" aria-label="Puntuación">
              {[1, 2, 3, 4, 5].map((n) => (
                <button
                  key={n}
                  type="button"
                  className={n <= rating ? 'rv-pick rv-pick--on' : 'rv-pick'}
                  onClick={() => setRating(n)}
                  role="radio"
                  aria-checked={n === rating}
                  aria-label={`${n} ${n === 1 ? 'estrella' : 'estrellas'}`}
                >
                  <Star size={22} />
                </button>
              ))}
            </div>
          </div>
          <textarea
            className="rv-textarea"
            placeholder="Contanos qué te pareció el producto…"
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            rows={3}
          />
          {submitError && <p className="rv-error">{submitError}</p>}
          <button type="submit" className="rv-submit" disabled={submitting}>
            {submitting ? 'Enviando…' : 'Enviar reseña'}
          </button>
        </form>
      ) : (
        <p className="rv-login">
          <Link to="/cuenta/ingresar">Iniciá sesión</Link> para dejar tu reseña.
        </p>
      )}

      {/* Listado */}
      {loading ? (
        <p className="rv-status">Cargando reseñas…</p>
      ) : loadError ? (
        <p className="rv-status">No se pudieron cargar las reseñas.</p>
      ) : data && data.reviews.length > 0 ? (
        <ul className="rv-list">
          {data.reviews.map((r) => (
            <li key={r.id} className="rv-item">
              <div className="rv-item-head">
                <span className="rv-author">{authorName(r.userEmail)}</span>
                <Stars value={r.rating} />
                <time className="rv-date" dateTime={r.createdAt}>
                  {dateFmt.format(new Date(r.createdAt))}
                </time>
              </div>
              {r.comment && <p className="rv-comment">{r.comment}</p>}
            </li>
          ))}
        </ul>
      ) : (
        <p className="rv-status">Todavía no hay reseñas. ¡Sé el primero!</p>
      )}
    </section>
  );
}
