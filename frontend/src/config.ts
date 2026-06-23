/**
 * Configuración de la app. La URL del backend se toma de VITE_API_BASE_URL
 * (.env); si no está, usa el default de desarrollo (localhost:8080).
 */
export const API_BASE_URL: string =
  import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';
