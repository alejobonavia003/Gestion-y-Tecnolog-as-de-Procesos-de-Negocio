import { useState, type FormEvent } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { ApiError, api } from '../../api/cliente'
import { useAuth } from '../../auth/useAuth'

export function LoginEmpresa() {
  const [usuario, setUsuario] = useState('')
  const [clave, setClave] = useState('')
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)

  const { refrescar } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const destino = (location.state as { desde?: string } | null)?.desde ?? '/empresa/cursos'

  async function ingresar(e: FormEvent) {
    e.preventDefault()
    setError('')
    setEnviando(true)
    try {
      await api('/sesiones/empresa', {
        method: 'POST',
        body: JSON.stringify({ usuario, clave }),
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
      <h1>Acceso empresas</h1>
      <p className="ayuda">
        La cuenta se habilita cuando la empresa acepta una propuesta de capacitación.
      </p>

      <form onSubmit={ingresar} className="formulario">
        <label>
          Usuario
          <input
            value={usuario}
            onChange={(e) => setUsuario(e.target.value)}
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
