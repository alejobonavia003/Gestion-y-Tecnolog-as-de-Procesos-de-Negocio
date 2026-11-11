import { useState } from 'react'
import { ApiError } from '../../api/cliente'
import { registrarFinDictado } from '../../api/tareas'
import type { TareaDetalle } from '../../api/tipos'
import { DatosCurso } from '../DatosCurso'

type Props = {
  tarea: TareaDetalle
  alCompletar: (mensaje: string) => void
}

/**
 * Tarea "Desarrollar curso" (docente elegido). La tarea aparece cuando llega la fecha
 * acordada; el docente la completa al terminar el dictado.
 */
export function FormDictado({ tarea, alCompletar }: Props) {
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [confirmando, setConfirmando] = useState(false)

  async function registrar() {
    setError('')
    setEnviando(true)
    try {
      await registrarFinDictado(tarea.taskId)
      alCompletar('Registraste la finalización del dictado. Sigue el informe de resultados.')
    } catch (err) {
      setError(
        err instanceof ApiError && err.status === 409
          ? 'Todavía no se puede registrar: el curso no está en desarrollo.'
          : 'No se pudo registrar la finalización.',
      )
      setEnviando(false)
    }
  }

  return (
    <>
      <DatosCurso curso={tarea.contexto.curso} />

      <div className="bloque">
        <h2>Finalización del dictado</h2>
        <p className="ayuda">Cuando termines de dictar el curso, registralo acá.</p>

        {error && <p className="error">{error}</p>}

        {confirmando ? (
          <div className="acciones">
            <button onClick={registrar} disabled={enviando}>
              {enviando ? 'Registrando…' : 'Sí, terminé el dictado'}
            </button>
            <button className="secundario" onClick={() => setConfirmando(false)} disabled={enviando}>
              Cancelar
            </button>
          </div>
        ) : (
          <button onClick={() => setConfirmando(true)}>Registrar finalización del dictado</button>
        )}
      </div>
    </>
  )
}
