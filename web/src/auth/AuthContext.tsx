import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { api } from '../api/cliente'
import type { Sesion } from '../api/tipos'
import { AuthContext } from './contexto'

/** Pide al backend la sesión actual. Si no hay (401) o el backend está caído, devuelve null. */
function obtenerSesion(): Promise<Sesion | null> {
  return api<Sesion>('/sesiones/actual').catch(() => null)
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [sesion, setSesion] = useState<Sesion | null>(null)
  const [cargando, setCargando] = useState(true)

  // Al abrir la web: averiguar si ya hay una sesión iniciada (cookie)
  useEffect(() => {
    obtenerSesion()
      .then(setSesion)
      .finally(() => setCargando(false))
  }, [])

  const refrescar = useCallback(async () => {
    setSesion(await obtenerSesion())
  }, [])

  const cerrarSesion = useCallback(async () => {
    try {
      await api<void>('/sesiones/actual', { method: 'DELETE' })
    } finally {
      setSesion(null)
    }
  }, [])

  return (
    <AuthContext.Provider value={{ sesion, cargando, refrescar, cerrarSesion }}>
      {children}
    </AuthContext.Provider>
  )
}
