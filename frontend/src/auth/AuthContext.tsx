import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, TOKEN_KEY } from '../lib/api';
import type { AuthResponse, LoginRequest, RegisterRequest, Role, UserResponse } from '../models/auth';

// ---------------------------------------------------------------------------
// JWT helpers (decode base64url, validar expiración). NO verifican firma
// (eso es del backend); solo leemos el payload para poblar el usuario.
// ---------------------------------------------------------------------------
interface JwtPayload {
  sub: string; // email
  role: string; // 'ADMIN' | 'CLIENTE'
  exp: number; // unix seconds
}

interface AuthUser {
  email: string;
  role: Role;
}

function decodeToken(token: string): JwtPayload | null {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return null;
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(atob(base64)) as JwtPayload;
  } catch {
    return null;
  }
}

function userFromToken(token: string): AuthUser | null {
  const payload = decodeToken(token);
  if (!payload) return null;
  const nowSec = Math.floor(Date.now() / 1000);
  if (payload.exp <= nowSec) return null; // expirado
  return { email: payload.sub, role: payload.role as Role };
}

// ---------------------------------------------------------------------------
// Context
// ---------------------------------------------------------------------------
interface AuthContextValue {
  user: AuthUser | null;
  isAuthenticated: boolean;
  isAdmin: boolean;
  login: (credentials: LoginRequest) => Promise<void>;
  register: (payload: RegisterRequest) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(() => {
    const token = localStorage.getItem(TOKEN_KEY);
    return token ? userFromToken(token) : null;
  });

  // Al montar: si el token guardado es inválido/expirado, limpiarlo.
  useEffect(() => {
    const token = localStorage.getItem(TOKEN_KEY);
    if (token && !userFromToken(token)) {
      localStorage.removeItem(TOKEN_KEY);
      setUser(null);
    }
  }, []);

  const handleAuthResponse = useCallback((res: AuthResponse) => {
    const u = userFromToken(res.token);
    if (!u) return; // token inválido/expirado → tratar como fallo
    localStorage.setItem(TOKEN_KEY, res.token);
    setUser(u);
  }, []);

  const login = useCallback(
    async (credentials: LoginRequest) => {
      const { data } = await api.post<AuthResponse>('/api/auth/login', credentials);
      handleAuthResponse(data);
    },
    [handleAuthResponse],
  );

  const register = useCallback(
    async (payload: RegisterRequest) => {
      // El backend hace alta y luego esperamos auto-login (igual que en Angular).
      await api.post<UserResponse>('/api/auth/register', payload);
      const { data } = await api.post<AuthResponse>('/api/auth/login', {
        email: payload.email,
        password: payload.password,
      });
      handleAuthResponse(data);
    },
    [handleAuthResponse],
  );

  const logout = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY);
    setUser(null);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isAuthenticated: user !== null,
      isAdmin: user?.role === 'ADMIN',
      login,
      register,
      logout,
    }),
    [user, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth debe usarse dentro de <AuthProvider>');
  return ctx;
}
