import { useState, type FormEvent } from 'react'
import { ApiError } from '../../api/cliente'
import { definirDocente } from '../../api/tareas'
import type { TareaDetalle } from '../../api/tipos'
import { DatosCurso } from '../DatosCurso'

type Props = {
  tarea: TareaDetalle
  alCompletar: (mensaje: string) => void
}

/**
 * Tarea "Definir docente" (jefe del equipo docente): elige uno de los tres docentes,
 * él incluido. El elegido queda asignado a "Desarrollar curso".
 */
export function FormDocente({ tarea, alCompletar }: Props) {
  const docentes = tarea.contexto.docentes ?? []
  const [elegido, setElegido] = useState('')
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)

  async function confirmar(e: FormEvent) {
    e.preventDefault()
    if (!elegido) {
      setError('Elegí un docente.')
      return
    }
    setError('')
    setEnviando(true)
    try {
      await definirDocente(tarea.taskId, elegido)
      const nombre = docentes.find((d) => d.usuarioId === elegido)?.nombre ?? elegido
      alCompletar(`${nombre} quedó asignado para desarrollar el curso.`)
    } catch (err) {
      setError(err instanceof ApiError && err.status === 400 ? err.message : 'No se pudo asignar el docente.')
      setEnviando(false)
    }
  }

  return (
    <>
      <DatosCurso curso={tarea.contexto.curso} />

      <form onSubmit={confirmar} className="formulario bloque">
        <h2>¿Quién va a dictar el curso?</h2>
        <div className="opciones">
          {docentes.map((d) => (
            <label key={d.usuarioId} className={`opcion ${elegido === d.usuarioId ? 'marcada' : ''}`}>
              <input
                type="radio"
                name="docente"
                value={d.usuarioId}
                checked={elegido === d.usuarioId}
                onChange={() => setElegido(d.usuarioId)}
              />
              <span>
                <strong>{d.nombre}</strong>
                <span className="detalle">
                  {d.usuarioId}
                  {d.esJefe ? ' · jefe del equipo (vos)' : ''}
                </span>
              </span>
            </label>
          ))}
        </div>

        {error && <p className="error">{error}</p>}

        <button type="submit" disabled={enviando || !elegido}>
          {enviando ? 'Asignando…' : 'Asignar docente y completar'}
        </button>
      </form>
    </>
  )
}
