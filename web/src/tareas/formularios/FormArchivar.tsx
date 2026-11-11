import { useState } from 'react'
import { completarTarea } from '../../api/tareas'
import type { TareaDetalle } from '../../api/tipos'

type Props = {
  tarea: TareaDetalle
  alCompletar: (mensaje: string) => void
}

const MOTIVOS = {
  RECHAZADA: 'La empresa rechazó la propuesta.',
  SIN_RESPUESTA: 'La empresa no respondió después de dos recordatorios.',
}

/** Tarea "Archivar solicitud": se confirma y el proceso termina (rechazado o sin respuesta). */
export function FormArchivar({ tarea, alCompletar }: Props) {
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)
  const motivo = tarea.contexto.motivoArchivo

  async function archivar() {
    setError('')
    setEnviando(true)
    try {
      await completarTarea(tarea.taskId)
      alCompletar('La solicitud quedó archivada.')
    } catch {
      setError('No se pudo archivar la solicitud. Probá de nuevo.')
      setEnviando(false)
    }
  }

  return (
    <div className="bloque">
      <h2>Archivar solicitud</h2>
      <p>
        <strong>Motivo:</strong> {motivo ? MOTIVOS[motivo] : 'No informado.'}
      </p>
      <p className="ayuda">Al archivarla, la solicitud se cierra y el proceso termina.</p>

      {error && <p className="error">{error}</p>}

      <button onClick={archivar} disabled={enviando}>
        {enviando ? 'Archivando…' : 'Archivar y completar'}
      </button>
    </div>
  )
}
