import { useCallback, useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { ApiError } from '../../api/cliente'
import { listarTareas, reclamarTarea } from '../../api/tareas'
import type { TareaResumen } from '../../api/tipos'
import { TEXTO_DONDE, infoTarea } from '../../tareas/catalogo'
import { formatearFecha } from '../../tareas/formato'

/** Pide en paralelo las tareas asignadas y las disponibles para el grupo. */
async function obtenerTareas() {
  const [mias, libres] = await Promise.all([listarTareas('asignadas'), listarTareas('disponibles')])
  return { asignadas: mias.items, disponibles: libres.items }
}

export function Bandeja() {
  const [asignadas, setAsignadas] = useState<TareaResumen[]>([])
  const [disponibles, setDisponibles] = useState<TareaResumen[]>([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')
  // Si venimos de completar una tarea, DetalleTarea manda un aviso para mostrar
  const location = useLocation()
  const [aviso, setAviso] = useState((location.state as { aviso?: string } | null)?.aviso ?? '')
  const [reclamando, setReclamando] = useState<string | null>(null)

  const mostrar = useCallback((resultado: { asignadas: TareaResumen[]; disponibles: TareaResumen[] }) => {
    setAsignadas(resultado.asignadas)
    setDisponibles(resultado.disponibles)
    setError('')
  }, [])

  const mostrarError = useCallback(() => {
    setError('No se pudieron cargar las tareas. Probá de nuevo en unos minutos.')
  }, [])

  // Vuelve a pedir las dos bandejas (botón Actualizar y después de reclamar)
  const cargar = useCallback(() => obtenerTareas().then(mostrar).catch(mostrarError), [mostrar, mostrarError])

  // Primera carga al entrar a la página
  useEffect(() => {
    obtenerTareas()
      .then(mostrar)
      .catch(mostrarError)
      .finally(() => setCargando(false))
  }, [mostrar, mostrarError])

  async function reclamar(tarea: TareaResumen) {
    setReclamando(tarea.taskId)
    setAviso('')
    try {
      await reclamarTarea(tarea.taskId)
      setAviso(`Reclamaste "${infoTarea(tarea.taskDefinitionKey).titulo}". Ahora está en tus tareas.`)
    } catch (err) {
      setAviso(
        err instanceof ApiError && err.codigo === 'YA_RECLAMADA'
          ? 'Otra persona reclamó esa tarea antes que vos. Actualizamos la lista.'
          : 'No se pudo reclamar la tarea.',
      )
    } finally {
      setReclamando(null)
      await cargar() // siempre refrescar: la tarea cambió de lista o ya no existe
    }
  }

  if (cargando) return <p className="cargando">Cargando tareas…</p>

  return (
    <section>
      <div className="encabezado">
        <h1>Mis tareas</h1>
        <button className="secundario" onClick={cargar}>
          Actualizar
        </button>
      </div>

      {error && <p className="error">{error}</p>}
      {aviso && <p className="aviso">{aviso}</p>}

      <h2>Asignadas a mí ({asignadas.length})</h2>
      {asignadas.length === 0 ? (
        <p className="ayuda">No tenés tareas asignadas.</p>
      ) : (
        <ul className="lista-tareas">
          {asignadas.map((t) => {
            const info = infoTarea(t.taskDefinitionKey)
            return (
              <li key={t.taskId} className="tarea">
                <div>
                  <strong>{info.titulo}</strong>
                  <span className="detalle">
                    {t.cursoNombre} · {t.empresaNombre} · {formatearFecha(t.creada)}
                  </span>
                </div>
                {info.dondeSeCompleta === 'web' ? (
                  <Link to={`/tareas/${t.taskId}`} className="boton">
                    Abrir
                  </Link>
                ) : (
                  <span className="etiqueta">{TEXTO_DONDE[info.dondeSeCompleta]}</span>
                )}
              </li>
            )
          })}
        </ul>
      )}

      <h2>Disponibles para mi grupo ({disponibles.length})</h2>
      {disponibles.length === 0 ? (
        <p className="ayuda">No hay tareas libres en tu grupo.</p>
      ) : (
        <ul className="lista-tareas">
          {disponibles.map((t) => {
            const info = infoTarea(t.taskDefinitionKey)
            return (
              <li key={t.taskId} className="tarea">
                <div>
                  <strong>{info.titulo}</strong>
                  <span className="detalle">
                    {t.cursoNombre} · {t.empresaNombre} · {formatearFecha(t.creada)}
                  </span>
                </div>
                {info.dondeSeCompleta !== 'web' ? (
                  <span className="etiqueta">{TEXTO_DONDE[info.dondeSeCompleta]}</span>
                ) : (
                  t.reclamable && (
                    <button onClick={() => reclamar(t)} disabled={reclamando !== null}>
                      {reclamando === t.taskId ? 'Reclamando…' : 'Reclamar'}
                    </button>
                  )
                )}
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}
