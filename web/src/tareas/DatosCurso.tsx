import type { DatosCursoTarea } from '../api/tipos'
import { formatearFecha } from './formato'

/** Bloque con los datos del curso que se muestra en las tareas de docentes y de cierre. */
export function DatosCurso({ curso }: { curso?: DatosCursoTarea }) {
  if (!curso) return null
  return (
    <div className="bloque">
      <h2>Datos del curso</h2>
      <dl className="datos">
        <dt>Descripción</dt>
        <dd>{curso.descripcion}</dd>
        <dt>Participantes</dt>
        <dd>{curso.cantidadParticipantes}</dd>
        <dt>Fecha de inicio</dt>
        <dd>{curso.fechaCurso ? formatearFecha(curso.fechaCurso) : 'A confirmar'}</dd>
      </dl>
    </div>
  )
}
