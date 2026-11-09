import { createContext } from 'react'
import type { Sesion } from '../api/tipos'

export type AuthValor = {
  sesion: Sesion | null
  cargando: boolean
  /** Vuelve a consultar al backend quién está logueado (se usa después del login). */
  refrescar: () => Promise<void>
  cerrarSesion: () => Promise<void>
}

export const AuthContext = createContext<AuthValor | null>(null)
