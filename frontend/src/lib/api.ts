import axios from 'axios';
import { API_BASE_URL } from '../config';

/** Clave del JWT en localStorage (misma que usa AuthContext). */
export const TOKEN_KEY = 'token';

/**
 * Cliente HTTP de la app. baseURL = backend (config). Un interceptor adjunta
 * el Bearer token si existe — equivalente al authInterceptor de Angular.
 */
export const api = axios.create({ baseURL: API_BASE_URL });

api.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});
