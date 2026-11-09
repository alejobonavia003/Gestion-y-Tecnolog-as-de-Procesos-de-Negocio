import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './useAuth'

type Props = {
  tipo: 'PERSONAL' | 'EMPRESA'
}

/**
 * Deja pasar solo si hay sesión del tipo pedido; si no, manda al login que corresponde.
 * Ojo: esto es solo comodidad de la UI. La seguridad real la valida el backend.
 */
export function RutaProtegida({ tipo }: Props) {
  const { sesion, cargando } = useAuth()
  const location = useLocation()

  if (cargando) return <p className="cargando">Cargando…</p>

  if (!sesion || sesion.tipo !== tipo) {
    const login = tipo === 'PERSONAL' ? '/login' : '/empresa/login'
    return <Navigate to={login} replace state={{ desde: location.pathname }} />
  }

  return <Outlet />
}
