import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'

export function Layout() {
  const { sesion, cerrarSesion } = useAuth()
  const navigate = useNavigate()

  async function salir() {
    await cerrarSesion()
    navigate('/')
  }

  return (
    <>
      <header className="cabecera">
        <Link to="/" className="marca">
          Institución de Capacitación
        </Link>

        <nav className="menu">
          {!sesion && (
            <>
              <NavLink to="/solicitar">Solicitar capacitación</NavLink>
              <NavLink to="/empresa/login">Acceso empresas</NavLink>
              <NavLink to="/login">Acceso personal</NavLink>
            </>
          )}

          {sesion?.tipo === 'EMPRESA' && <NavLink to="/empresa/cursos">Mis cursos</NavLink>}

          {sesion?.tipo === 'PERSONAL' && <NavLink to="/tareas">Mis tareas</NavLink>}

          {sesion && (
            <>
              <span className="usuario">
                {sesion.tipo === 'EMPRESA' ? sesion.nombre : sesion.usuarioId}
              </span>
              <button onClick={salir}>Salir</button>
            </>
          )}
        </nav>
      </header>

      <main className="contenido">
        <Outlet />
      </main>
    </>
  )
}
