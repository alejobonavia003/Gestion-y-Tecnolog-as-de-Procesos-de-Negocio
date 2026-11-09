import { useState, type FormEvent } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { ApiError, api } from '../api/cliente'
import { useAuth } from '../auth/useAuth'

export function LoginPersonal() {
  const [usuarioId, setUsuarioId] = useState('')
  const [clave, setClave] = useState('')
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)

  const { refrescar } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  // Si lo mandó acá una ruta protegida, vuelve a esa ruta después del login
  const destino = (location.state as { desde?: string } | null)?.desde ?? '/tareas'

  async function ingresar(e: FormEvent) {
    e.preventDefault()
    setError('')
    setEnviando(true)
    try {
      await api('/sesiones/personal', {
        method: 'POST',
        body: JSON.stringify({ usuarioId, clave }),
      })
      await refrescar()
      navigate(destino, { replace: true })
    } catch (err) {
      setError(
        err instanceof ApiError && err.status === 401
          ? 'Usuario o contraseña incorrectos.'
          : 'No se pudo conectar con el sistema. Probá de nuevo en unos minutos.',
      )
    } finally {
      setEnviando(false)
    }
  }

  return (
    <section className="tarjeta angosta">
      <h1>Acceso del personal</h1>
      <p className="ayuda">Ingresá con tu usuario y contraseña de Flowable.</p>

      <form onSubmit={ingresar} className="formulario">
        <label>
          Usuario
          <input
            value={usuarioId}
            onChange={(e) => setUsuarioId(e.target.value)}
            autoComplete="username"
            required
          />
        </label>

        <label>
          Contraseña
          <input
            type="password"
            value={clave}
            onChange={(e) => setClave(e.target.value)}
            autoComplete="current-password"
            required
          />
        </label>

        {error && <p className="error">{error}</p>}

        <button type="submit" disabled={enviando}>
          {enviando ? 'Ingresando…' : 'Ingresar'}
        </button>
      </form>
    </section>
  )
}
