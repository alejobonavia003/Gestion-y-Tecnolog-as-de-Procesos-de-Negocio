import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError } from '../../api/cliente'
import { listarActividades, obtenerCurso } from '../../api/cursos'
import { abrirPdfPropuesta, listarPropuestas } from '../../api/tareas'
import type { Actividad, CursoDetalle, Propuesta } from '../../api/tipos'
import { EstadoCurso } from '../../cursos/EstadoCurso'
import { ETAPAS, indiceEtapa, infoEstado } from '../../cursos/estados'
import { formatearFecha, formatearImporte } from '../../tareas/formato'

const TIPO_ACTIVIDAD: Record<Actividad['tipo'], string> = {
  TAREA_USUARIO: 'En curso',
  ESPERA_MENSAJE: 'Esperando a la empresa',
  AUTOMATICA: 'Automática',
}

export function DetalleCurso() {
  const { cursoId = '' } = useParams()
  const [curso, setCurso] = useState<CursoDetalle | null>(null)
  const [actividades, setActividades] = useState<Actividad[]>([])
  const [propuestas, setPropuestas] = useState<Propuesta[]>([])
  const [error, setError] = useState('')

  const mostrarActividades = useCallback((r: { items: Actividad[] }) => setActividades(r.items), [])

  useEffect(() => {
    obtenerCurso(cursoId)
      .then(setCurso)
      .catch((err) =>
        setError(
          err instanceof ApiError && err.status === 404
            ? 'No encontramos ese curso entre los de tu empresa.'
            : 'No se pudo cargar el curso.',
        ),
      )
    listarActividades(cursoId)
      .then(mostrarActividades)
      .catch(() => setActividades([]))
    listarPropuestas(cursoId)
      .then((r) => setPropuestas(r.items))
      .catch(() => setPropuestas([]))
  }, [cursoId, mostrarActividades])

  function actualizarActividades() {
    listarActividades(cursoId)
      .then(mostrarActividades)
      .catch(() => setActividades([]))
  }

  async function verPdf(version: number) {
    try {
      await abrirPdfPropuesta(cursoId, version)
    } catch {
      setError('No se pudo abrir el PDF.')
    }
  }

  if (error) {
    return (
      <section>
        <p className="error">{error}</p>
        <Link to="/empresa/cursos">← Volver a mis cursos</Link>
      </section>
    )
  }

  if (!curso) return <p className="cargando">Cargando curso…</p>

  const cancelado = curso.estado === 'CANCELADO' || curso.estado === 'CANCELACION_EN_CURSO'
  const etapaActual = indiceEtapa(curso.estado)
  const aceptada = propuestas.find((p) => p.estado === 'ACEPTADA')

  return (
    <section>
      <Link to="/empresa/cursos">← Volver a mis cursos</Link>
      <div className="encabezado">
        <h1>{curso.nombre}</h1>
        <EstadoCurso estado={curso.estado} etiqueta={curso.estadoEtiqueta} />
      </div>

      {cancelado ? (
        <div className="bloque destacado">
          <p>Este curso fue cancelado. Se liberaron los recursos que se habían reservado.</p>
        </div>
      ) : (
        <ol className="progreso">
          {ETAPAS.map((etapa, i) => (
            <li
              key={etapa}
              className={i < etapaActual || curso.estado === 'FINALIZADO' ? 'hecha' : i === etapaActual ? 'actual' : ''}
            >
              {infoEstado(etapa).etiqueta}
            </li>
          ))}
        </ol>
      )}

      <div className="bloque">
        <h2>Datos del curso</h2>
        <dl className="datos">
          <dt>Descripción</dt>
          <dd>{curso.descripcion}</dd>
          <dt>Participantes</dt>
          <dd>{curso.cantidadParticipantes}</dd>
          <dt>Fecha de inicio</dt>
          <dd>{curso.fechaCurso ? formatearFecha(curso.fechaCurso) : 'A confirmar'}</dd>
          {aceptada && (
            <>
              <dt>Presupuesto</dt>
              <dd>{formatearImporte(aceptada.presupuesto.importe, aceptada.presupuesto.moneda)}</dd>
            </>
          )}
        </dl>
        {aceptada && (
          <button className="secundario espacio-arriba" onClick={() => verPdf(aceptada.version)}>
            Ver propuesta aceptada (PDF)
          </button>
        )}
      </div>

      {curso.estado === 'EN_PREPARACION' && (
        <div className="bloque">
          <div className="encabezado">
            <h2>Actividades en curso</h2>
            <button className="secundario" onClick={actualizarActividades}>
              Actualizar
            </button>
          </div>
          <p className="ayuda">La preparación avanza en paralelo: estas actividades se están realizando ahora.</p>
          {actividades.length === 0 ? (
            <p className="ayuda">No hay actividades pendientes en este momento.</p>
          ) : (
            <ul className="actividades">
              {actividades.map((a) => (
                <li key={a.activityId}>
                  <span>{a.nombre}</span>
                  <span className={`etiqueta ${a.tipo === 'ESPERA_MENSAJE' ? 'etiqueta-alerta' : ''}`}>
                    {TIPO_ACTIVIDAD[a.tipo]}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </section>
  )
}
