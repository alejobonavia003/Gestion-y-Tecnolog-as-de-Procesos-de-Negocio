import { useEffect, useState, type FormEvent } from 'react'
import { ApiError } from '../../api/cliente'
import { abrirPdfPropuesta, enviarPropuesta, listarPropuestas } from '../../api/tareas'
import type { Propuesta, TareaDetalle } from '../../api/tipos'
import { formatearFecha, formatearImporte, isoALocal, localAIso } from '../formato'

type Props = {
  tarea: TareaDetalle
  alCompletar: (mensaje: string) => void
}

/**
 * Formulario de las tareas "Registrar y elaborar propuesta" (Propuesta) y
 * "Revisar propuesta" (Revisar). En Revisar arranca con los datos de la versión vigente
 * y muestra las observaciones de la empresa y las versiones anteriores.
 */
export function FormPropuesta({ tarea, alCompletar }: Props) {
  const esRevision = tarea.taskDefinitionKey === 'Revisar'
  const { solicitud, propuestaVigente, observacionesEmpresa } = tarea.contexto

  const [programa, setPrograma] = useState(propuestaVigente?.programa ?? '')
  const [importe, setImporte] = useState(propuestaVigente?.presupuesto.importe ?? '')
  const [moneda, setMoneda] = useState(propuestaVigente?.presupuesto.moneda ?? 'ARS')
  const [fechas, setFechas] = useState<string[]>(
    propuestaVigente ? propuestaVigente.fechasPosibles.map(isoALocal) : [''],
  )
  const [versiones, setVersiones] = useState<Propuesta[]>([])
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)

  // En la revisión, traer el historial de versiones del curso
  useEffect(() => {
    if (!esRevision) return
    listarPropuestas(tarea.cursoId)
      .then((r) => setVersiones(r.items))
      .catch(() => setVersiones([]))
  }, [esRevision, tarea.cursoId])

  function cambiarFecha(indice: number, valor: string) {
    setFechas((anteriores) => anteriores.map((f, i) => (i === indice ? valor : f)))
  }

  async function verPdf(version: number) {
    try {
      await abrirPdfPropuesta(tarea.cursoId, version)
    } catch {
      setError('No se pudo abrir el PDF.')
    }
  }

  async function enviar(e: FormEvent) {
    e.preventDefault()
    setError('')

    const importeNumero = Number(importe)
    if (!Number.isFinite(importeNumero) || importeNumero <= 0) {
      setError('El presupuesto tiene que ser un número mayor a 0.')
      return
    }
    const fechasCompletas = fechas.filter((f) => f !== '')
    if (fechasCompletas.length === 0) {
      setError('Agregá al menos una fecha posible.')
      return
    }

    setEnviando(true)
    try {
      const creada = await enviarPropuesta(tarea.taskId, {
        programa: programa.trim(),
        presupuesto: { importe: importeNumero.toFixed(2), moneda },
        fechasPosibles: fechasCompletas.map(localAIso),
      })
      alCompletar(`Se generó la propuesta versión ${creada.propuestaVersion} con su PDF.`)
    } catch (err) {
      if (err instanceof ApiError && err.codigo === 'VERSION_DESACTUALIZADA') {
        setError(`${err.message} Volvé a la bandeja y abrí la tarea de nuevo.`)
      } else if (err instanceof ApiError && err.status === 400) {
        setError(err.message)
      } else {
        setError('No se pudo guardar la propuesta. Probá de nuevo.')
      }
    } finally {
      setEnviando(false)
    }
  }

  return (
    <>
      {solicitud && (
        <div className="bloque">
          <h2>Solicitud de la empresa</h2>
          <dl className="datos">
            <dt>Temática</dt>
            <dd>{solicitud.tematica}</dd>
            <dt>Descripción</dt>
            <dd>{solicitud.descripcion}</dd>
            <dt>Participantes</dt>
            <dd>{solicitud.cantidadEstimadaParticipantes}</dd>
            <dt>Fechas preferidas</dt>
            <dd>{solicitud.fechasPreferidas}</dd>
          </dl>
        </div>
      )}

      {esRevision && (
        <div className="bloque destacado">
          <h2>Modificaciones pedidas por la empresa</h2>
          <p>{observacionesEmpresa || 'La empresa no dejó observaciones.'}</p>
        </div>
      )}

      {esRevision && versiones.length > 0 && (
        <div className="bloque">
          <h2>Versiones anteriores</h2>
          <ul className="versiones">
            {versiones.map((v) => (
              <li key={v.version}>
                <span>
                  <strong>Versión {v.version}</strong> · {formatearImporte(v.presupuesto.importe, v.presupuesto.moneda)} ·{' '}
                  {formatearFecha(v.creada)} · {v.estado}
                </span>
                <button type="button" className="secundario" onClick={() => verPdf(v.version)}>
                  Ver PDF
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}

      <form onSubmit={enviar} className="formulario bloque">
        <h2>{esRevision ? 'Nueva versión de la propuesta' : 'Propuesta'}</h2>

        <label>
          Programa *
          <textarea
            rows={6}
            value={programa}
            onChange={(e) => setPrograma(e.target.value)}
            placeholder="Contenidos, módulos, carga horaria, modalidad…"
            required
          />
        </label>

        <div className="fila">
          <label>
            Presupuesto *
            <input
              type="number"
              min="0.01"
              step="0.01"
              value={importe}
              onChange={(e) => setImporte(e.target.value)}
              required
            />
          </label>
          <label>
            Moneda
            <select value={moneda} onChange={(e) => setMoneda(e.target.value)}>
              <option value="ARS">ARS</option>
              <option value="USD">USD</option>
            </select>
          </label>
        </div>

        <fieldset>
          <legend>Fechas posibles *</legend>
          {fechas.map((fecha, i) => (
            <div key={i} className="fila-fecha">
              <input
                type="datetime-local"
                value={fecha}
                onChange={(e) => cambiarFecha(i, e.target.value)}
                aria-label={`Fecha posible ${i + 1}`}
              />
              {fechas.length > 1 && (
                <button
                  type="button"
                  className="secundario"
                  onClick={() => setFechas((anteriores) => anteriores.filter((_, j) => j !== i))}
                >
                  Quitar
                </button>
              )}
            </div>
          ))}
          <button type="button" className="secundario" onClick={() => setFechas((a) => [...a, ''])}>
            + Agregar fecha
          </button>
        </fieldset>

        {error && <p className="error">{error}</p>}

        <button type="submit" disabled={enviando}>
          {enviando ? 'Generando…' : esRevision ? 'Generar nueva versión y completar' : 'Generar propuesta y completar'}
        </button>
      </form>
    </>
  )
}
