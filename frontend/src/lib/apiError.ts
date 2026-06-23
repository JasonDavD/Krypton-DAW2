import { isAxiosError } from 'axios';

/**
 * Extrae el mensaje del backend (campo `error` de ApiError, p.ej. "La categoría
 * tiene productos asociados") o devuelve el fallback si no es un error de axios.
 */
export function apiErrorMessage(err: unknown, fallback: string): string {
  if (isAxiosError(err)) {
    const data = err.response?.data as { error?: string } | undefined;
    return data?.error ?? fallback;
  }
  return fallback;
}
