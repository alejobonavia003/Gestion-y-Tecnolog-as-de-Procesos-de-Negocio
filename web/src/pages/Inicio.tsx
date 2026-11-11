import { Link } from 'react-router-dom'

export function Inicio() {
  return (
    <section>
      <h1>Capacitaciones para empresas</h1>
      <p>Diseñamos y dictamos cursos a medida para los equipos de tu empresa.</p>
      <Link to="/solicitar" className="boton">
        Solicitar una capacitación
      </Link>
    </section>
  )
}
