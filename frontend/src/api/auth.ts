import { api } from './client';
import type { AuthResponse, LoginRequest } from './types';

export async function login(payload: LoginRequest): Promise<AuthResponse> {
  const { data } = await api.post<AuthResponse>('/api/auth/login', payload);
  return data;
}
