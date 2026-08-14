import { createContext, useContext, useMemo, useState, type ReactNode } from 'react';
import { sessionStorageGc } from '../api/client';
import { login as loginApi } from '../api/auth';
import type { AuthResponse, Role } from '../api/types';

interface AuthUser {
  id: number;
  nom: string;
  prenom: string;
  email: string;
  role: Role;
}

interface AuthContextValue {
  user: AuthUser | null;
  isAuthenticated: boolean;
  login: (email: string, motDePasse: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

/**
 * Une session n'est valide que si le token ET l'utilisateur sont presents.
 * S'il en manque un, on repart de zero plutot que de laisser un etat batard.
 */
function loadUser(): AuthUser | null {
  const raw = sessionStorageGc.getUser();
  if (!raw || !sessionStorageGc.getToken()) {
    sessionStorageGc.clear();
    return null;
  }
  return JSON.parse(raw) as AuthUser;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(loadUser());

  const login = async (email: string, motDePasse: string) => {
    const res: AuthResponse = await loginApi({ email, motDePasse });
    const u: AuthUser = {
      id: res.id,
      nom: res.nom,
      prenom: res.prenom,
      email: res.email,
      role: res.role,
    };
    // Token et utilisateur poses ensemble, en une seule operation
    sessionStorageGc.save(res.token, u);
    setUser(u);
  };

  const logout = () => {
    sessionStorageGc.clear();
    setUser(null);
  };

  const value = useMemo<AuthContextValue>(
    () => ({ user, isAuthenticated: !!user, login, logout }),
    [user],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth doit etre utilise dans AuthProvider');
  }
  return ctx;
}
