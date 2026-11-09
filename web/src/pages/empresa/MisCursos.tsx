import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { listarCursos } from '../../api/cursos'
import type { CursoResumen } from '../../api/tipos'
import { useAuth } from '../../auth/useAuth'
import { EstadoCurso } from '../../cursos/EstadoCurso'
import { formatearFecha } from '../../tareas/formato'

export function MisCursos() {
  const { sesion } = useAuth()
  const [cursos, setCursos] = useState<CursoResumen[] | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    listarCursos()
      .then((r) => setCursos(r.items))
      .catch(() => setError('No se pudieron cargar los cursos. Probá de nuevo en unos minutos.'))
  }, [])

  if (error) return <p className="error">{error}</p>
  if (!cursos) return <p className="cargando">Cargando cursos…</p>

  return (
    <section>
      <h1>Mis cursos</h1>
      {sesion?.tipo === 'EMPRESA' && <p className="ayuda">{sesion.nombre}</p>}

      {cursos.length === 0 ? (
        <p className="ayuda">Todavía no tenés cursos.</p>
      ) : (
        <ul className="lista-tareas">
          {cursos.map((c) => (
            <li key={c.cursoId} className="tarea">
              <div>
                <strong>{c.nombre}</strong>
                <span className="detalle">
                  {c.fechaCurso ? `Inicio: ${formatearFecha(c.fechaCurso)}` : 'Fecha de inicio a confirmar'}
                </span>
              </div>
              <div className="acciones-fila">
                <EstadoCurso estado={c.estado} etiqueta={c.estadoEtiqueta} />
                <Link to={`/empresa/cursos/${c.cursoId}`} className="boton">
                  Ver
                </Link>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
