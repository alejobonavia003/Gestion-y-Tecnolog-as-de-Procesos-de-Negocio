import { useState, type FormEvent } from 'react'
import { ApiError } from '../../api/cliente'
import { completarTarea } from '../../api/tareas'
import type { TareaDetalle } from '../../api/tipos'

type Props = {
  tarea: TareaDetalle
  alCompletar: (mensaje: string) => void
}

/**
 * Tarea "Confeccionar lista definitiva": parte de la nómina que envió la empresa.
 * Se pueden corregir nombres, quitar o agregar participantes. No se verifica a cada uno
 * individualmente (lo aclara el enunciado).
 */
export function FormLista({ tarea, alCompletar }: Props) {
  const nomina = tarea.contexto.participantes ?? []
  const [nombres, setNombres] = useState<string[]>(nomina.map((p) => p.nombre))
  const [nuevo, setNuevo] = useState('')
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)

  function cambiar(indice: number, valor: string) {
    setNombres((anteriores) => anteriores.map((n, i) => (i === indice ? valor : n)))
  }

  function quitar(indice: number) {
    setNombres((anteriores) => anteriores.filter((_, i) => i !== indice))
  }

  function agregar() {
    if (!nuevo.trim()) return
    setNombres((anteriores) => [...anteriores, nuevo.trim()])
    setNuevo('')
  }

  async function confirmar(e: FormEvent) {
    e.preventDefault()
    setError('')
    const definitivos = nombres.map((n) => n.trim()).filter((n) => n !== '')
    if (definitivos.length === 0) {
      setError('La lista definitiva tiene que tener al menos un participante.')
      return
    }

    setEnviando(true)
    try {
      await completarTarea(tarea.taskId, { participantes: definitivos.map((nombre) => ({ nombre })) })
      alCompletar(`Lista definitiva confirmada con ${definitivos.length} participantes.`)
    } catch (err) {
      setError(err instanceof ApiError && err.status === 400 ? err.message : 'No se pudo guardar la lista.')
      setEnviando(false)
    }
  }

  return (
    <form onSubmit={confirmar} className="formulario bloque">
      <h2>Lista definitiva de participantes</h2>
      <p className="ayuda">
        La empresa envió {nomina.length} participantes en la nómina. Revisá la lista y confirmala.
      </p>

      {nombres.length === 0 ? (
        <p className="ayuda">La lista está vacía.</p>
      ) : (
        <ol className="participantes">
          {nombres.map((nombre, i) => (
            <li key={i}>
              <input
                value={nombre}
                onChange={(e) => cambiar(i, e.target.value)}
                aria-label={`Participante ${i + 1}`}
              />
              <button type="button" className="secundario" onClick={() => quitar(i)}>
                Quitar
              </button>
            </li>
          ))}
        </ol>
      )}

      <div className="fila-fecha">
        <input
          value={nuevo}
          onChange={(e) => setNuevo(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault() // que Enter agregue y no envíe el formulario
              agregar()
            }
          }}
          placeholder="Nombre y apellido"
          aria-label="Nuevo participante"
        />
        <button type="button" className="secundario" onClick={agregar}>
          + Agregar
        </button>
      </div>

      {error && <p className="error">{error}</p>}

      <button type="submit" disabled={enviando}>
        {enviando ? 'Guardando…' : `Confirmar lista (${nombres.filter((n) => n.trim()).length}) y completar`}
      </button>
    </form>
  )
}
