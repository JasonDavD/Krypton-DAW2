import { api } from '../../lib/api';
import type { CreateUserRequest, Role, UserResponse } from '../../models/auth';

/** GET /api/admin/users — lista completa (sin paginación). */
export async function listUsers(): Promise<UserResponse[]> {
  const { data } = await api.get<UserResponse[]>('/api/admin/users');
  return data;
}

/** POST /api/admin/users — alta de usuario (201). 409 si el email ya existe. */
export async function createUser(body: CreateUserRequest): Promise<UserResponse> {
  const { data } = await api.post<UserResponse>('/api/admin/users', body);
  return data;
}

/** PATCH /api/admin/users/{id}/role — cambia el rol. 422 si degradás al último admin. */
export async function updateUserRole(id: number, role: Role): Promise<UserResponse> {
  const { data } = await api.patch<UserResponse>(`/api/admin/users/${id}/role`, { role });
  return data;
}

/** PATCH /api/admin/users/{id}/status — activa/desactiva. 422 si desactivás al último admin. */
export async function updateUserStatus(id: number, active: boolean): Promise<UserResponse> {
  const { data } = await api.patch<UserResponse>(`/api/admin/users/${id}/status`, { active });
  return data;
}
