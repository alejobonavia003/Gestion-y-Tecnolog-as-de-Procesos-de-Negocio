import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../../api/cliente'
import { obtenerTarea } from '../../api/tareas'
import type { TareaDetalle } from '../../api/tipos'
import { TEXTO_DONDE, infoTarea } from '../../tareas/catalogo'
import { formatearFecha } from '../../tareas/formato'
import { FormArchivar } from '../../tareas/formularios/FormArchivar'
import { FormDictado } from '../../tareas/formularios/FormDictado'
import { FormDocente } from '../../tareas/formularios/FormDocente'
import { FormEnviarInforme } from '../../tareas/formularios/FormEnviarInforme'
import { FormLista } from '../../tareas/formularios/FormLista'
import { FormPropuesta } from '../../tareas/formularios/FormPropuesta'
import { FormRecursos } from '../../tareas/formularios/FormRecursos'

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

      {tarea.reclamable ? (
        <div className="bloque">
          <p>Esta tarea es del grupo y todavía nadie la reclamó.</p>
          <Link to="/tareas" className="boton">
            Ir a la bandeja para reclamarla
          </Link>
        </div>
      ) : (
        renderFormulario(tarea, alCompletar)
      )}
    </section>
  )
}

function renderFormulario(tarea: TareaDetalle, alCompletar: (mensaje: string) => void) {
  switch (tarea.taskDefinitionKey) {
    case 'registrar_elaborar_propuesta':
    case 'Propuesta':
    case 'revisar_propuesta':
    case 'Revisar':
      return <FormPropuesta tarea={tarea} alCompletar={alCompletar} />
    case 'archivar_solicitud':
    case 'Archivar':
      return <FormArchivar tarea={tarea} alCompletar={alCompletar} />
    case 'confeccionar_lista':
    case 'Lista':
      return <FormLista tarea={tarea} alCompletar={alCompletar} />
    case 'evaluar_recursos':
    case 'Activity_12i2hpf':
      return <FormRecursos tarea={tarea} alCompletar={alCompletar} />
    case 'definir_docente':
    case 'Activity_1018bdz':
      return <FormDocente tarea={tarea} alCompletar={alCompletar} />
    case 'desarrollar_curso':
    case 'Dictar':
      return <FormDictado tarea={tarea} alCompletar={alCompletar} />
    case 'archivar_expediente':
    case 'enviar_informe':
    case 'EnviarInforme':
      return <FormEnviarInforme tarea={tarea} alCompletar={alCompletar} />
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
