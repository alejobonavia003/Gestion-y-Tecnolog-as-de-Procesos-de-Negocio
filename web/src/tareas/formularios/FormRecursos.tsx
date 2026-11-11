import { useState, type FormEvent } from 'react'
import { ApiError } from '../../api/cliente'
import { completarTarea } from '../../api/tareas'
import type { TareaDetalle } from '../../api/tipos'

type Props = {
  tarea: TareaDetalle
  alCompletar: (mensaje: string) => void
}

// Los nombres de las variables tienen que coincidir con las condiciones del BPMN
// (${requiereAula}, ${requierePlataforma}, ${requiereEquipamiento}).
const OPCIONES = [
  {
    variable: 'requiereAula',
    titulo: 'Aula física',
    detalle: 'Se reserva automáticamente.',
  },
  {
    variable: 'requierePlataforma',
    titulo: 'Aula virtual',
    detalle: 'Genera la tarea "Solicitar aula virtual" (se completa desde Flowable).',
  },
  {
    variable: 'requiereEquipamiento',
    titulo: 'Equipamiento especial',
    detalle: 'Genera la tarea "Contratar equipamiento" (se completa desde Flowable).',
  },
] as const

type Variable = (typeof OPCIONES)[number]['variable']

/** Tarea "Evaluar recursos": hay que elegir al menos un recurso. El motor activa las ramas elegidas. */
export function FormRecursos({ tarea, alCompletar }: Props) {
  const [seleccion, setSeleccion] = useState<Record<Variable, boolean>>({
    requiereAula: false,
    requierePlataforma: false,
    requiereEquipamiento: false,
  })
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)

  const algunoElegido = Object.values(seleccion).some(Boolean)

  async function confirmar(e: FormEvent) {
    e.preventDefault()
    setError('')
    if (!algunoElegido) {
      setError('Elegí al menos un recurso.')
      return
    }

    setEnviando(true)
    try {
      await completarTarea(tarea.taskId, seleccion)
      const elegidos = OPCIONES.filter((o) => seleccion[o.variable]).map((o) => o.titulo.toLowerCase())
      alCompletar(`Recursos confirmados: ${elegidos.join(', ')}.`)
    } catch (err) {
      setError(err instanceof ApiError && err.status === 400 ? err.message : 'No se pudieron guardar los recursos.')
      setEnviando(false)
    }
  }

  return (
    <form onSubmit={confirmar} className="formulario bloque">
      <h2>Recursos necesarios</h2>
      <p className="ayuda">Elegí uno o más. Se van a gestionar todos los que marques.</p>

      <div className="opciones">
        {OPCIONES.map((o) => (
          <label key={o.variable} className={`opcion ${seleccion[o.variable] ? 'marcada' : ''}`}>
            <input
              type="checkbox"
              checked={seleccion[o.variable]}
              onChange={(e) => setSeleccion((s) => ({ ...s, [o.variable]: e.target.checked }))}
            />
            <span>
              <strong>{o.titulo}</strong>
              <span className="detalle">{o.detalle}</span>
            </span>
          </label>
        ))}
      </div>

      {error && <p className="error">{error}</p>}

      <button type="submit" disabled={enviando || !algunoElegido}>
        {enviando ? 'Guardando…' : 'Confirmar recursos y completar'}
      </button>
    </form>
  )
}
