import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../../api/cliente'
import { obtenerTarea } from '../../api/tareas'
import type { TareaDetalle } from '../../api/tipos'
import { TEXTO_DONDE, infoTarea } from '../../tareas/catalogo'
import { formatearFecha } from '../../tareas/formato'
import { FormArchivar } from '../../tareas/formularios/FormArchivar'
import { FormPropuesta } from '../../tareas/formularios/FormPropuesta'

/** Abre una tarea y muestra el formulario que corresponde según su taskDefinitionKey. */
export function DetalleTarea() {
  const { taskId } = useParams()
  const navigate = useNavigate()
  const [tarea, setTarea] = useState<TareaDetalle | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!taskId) return
    obtenerTarea(taskId)
      .then(setTarea)
      .catch((err) =>
        setError(
          err instanceof ApiError && (err.status === 404 || err.status === 403)
            ? 'La tarea no existe, ya fue completada o no te corresponde.'
            : 'No se pudo cargar la tarea.',
        ),
      )
  }, [taskId])

  /** Cuando un formulario termina bien, volvemos a la bandeja con un aviso. */
  function alCompletar(mensaje: string) {
    navigate('/tareas', { state: { aviso: mensaje } })
  }

  if (error) {
    return (
      <section>
        <p className="error">{error}</p>
        <Link to="/tareas">← Volver a mis tareas</Link>
      </section>
    )
  }

  if (!tarea) return <p className="cargando">Cargando tarea…</p>

  const info = infoTarea(tarea.taskDefinitionKey)

  return (
    <section>
      <Link to="/tareas">← Volver a mis tareas</Link>
      <h1>{info.titulo}</h1>
      <p className="ayuda">
        {tarea.cursoNombre} · {tarea.empresaNombre} · creada el {formatearFecha(tarea.creada)}
      </p>

      {renderFormulario(tarea, alCompletar)}
    </section>
  )
}

function renderFormulario(tarea: TareaDetalle, alCompletar: (mensaje: string) => void) {
  switch (tarea.taskDefinitionKey) {
    case 'Propuesta':
    case 'Revisar':
      return <FormPropuesta tarea={tarea} alCompletar={alCompletar} />
    case 'Archivar':
      return <FormArchivar tarea={tarea} alCompletar={alCompletar} />
    default: {
      const info = infoTarea(tarea.taskDefinitionKey)
      return (
        <p className="etiqueta">
          {info.dondeSeCompleta === 'web'
            ? 'El formulario de esta tarea todavía no está hecho.'
            : TEXTO_DONDE[info.dondeSeCompleta]}
        </p>
      )
    }
  }
}
