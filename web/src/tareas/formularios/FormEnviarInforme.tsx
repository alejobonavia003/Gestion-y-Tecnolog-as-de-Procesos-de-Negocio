import { useState } from 'react'
import { abrirPdfInforme, completarTarea } from '../../api/tareas'
import type { TareaDetalle } from '../../api/tipos'
import { DatosCurso } from '../DatosCurso'

type Props = {
  tarea: TareaDetalle
  alCompletar: (mensaje: string) => void
}

/**
 * Tarea "Enviar informe a empresa y archivar expediente" (jefe administrativo).
 * Muestra el informe que elaboró el jefe docente y, al completarla, se envía a la empresa,
 * se archiva el expediente y el motor libera los recursos del curso.
 */
export function FormEnviarInforme({ tarea, alCompletar }: Props) {
  const informe = tarea.contexto.informe
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)

  async function verPdf() {
    try {
      await abrirPdfInforme(tarea.cursoId)
    } catch {
      setError('No se pudo abrir el informe.')
    }
  }

  async function enviar() {
    setError('')
    setEnviando(true)
    try {
      await completarTarea(tarea.taskId)
      alCompletar('Informe enviado a la empresa y expediente archivado. Se liberan los recursos del curso.')
    } catch {
      setError('No se pudo completar la tarea.')
      setEnviando(false)
    }
  }

  return (
    <>
      <DatosCurso curso={tarea.contexto.curso} />

      <div className="bloque">
        <h2>Informe de resultados</h2>
        {informe ? (
          <>
            <p className="texto-informe">{informe.resumen}</p>
            {informe.pdfDisponible && (
              <button className="secundario" onClick={verPdf}>
                Ver informe (PDF)
              </button>
            )}
          </>
        ) : (
          <p className="ayuda">El informe todavía no está disponible.</p>
        )}
      </div>

      <div className="bloque">
        <h2>Enviar y archivar</h2>
        <p className="ayuda">
          Al completar, el informe se envía a la empresa, se archiva el expediente y se liberan los recursos.
        </p>
        {error && <p className="error">{error}</p>}
        <button onClick={enviar} disabled={enviando || !informe}>
          {enviando ? 'Enviando…' : 'Enviar informe y archivar expediente'}
        </button>
      </div>
    </>
  )
}
