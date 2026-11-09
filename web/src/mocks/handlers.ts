import { http, HttpResponse } from 'msw/http'
import type { ContextoTarea, NuevaPropuesta, NuevaSolicitud, Sesion, SolicitudCreada } from '../api/tipos'
import {
  CLAVE_DEMO,
  cuentasEmpresa,
  cursos,
  guardarDatos,
  motivosArchivo,
  personal,
  propuestas,
  solicitudes,
  tareas,
  type TareaMock,
} from './datos'
import { generarPdf } from './pdf'

const API = '/api/v1'

// La "cookie" de sesión del mock se guarda en localStorage para que sobreviva a recargar la página
const CLAVE_STORAGE = 'mock-sesion'

function leerSesion(): Sesion | null {
  const guardada = localStorage.getItem(CLAVE_STORAGE)
  return guardada ? (JSON.parse(guardada) as Sesion) : null
}

function guardarSesion(sesion: Sesion | null) {
  if (sesion) localStorage.setItem(CLAVE_STORAGE, JSON.stringify(sesion))
  else localStorage.removeItem(CLAVE_STORAGE)
}

function error(status: number, codigo: string, mensaje: string) {
  return HttpResponse.json({ codigo, mensaje }, { status })
}

/** Convierte una tarea del mock al formato que devuelve la API. */
function aResumen(t: TareaMock) {
  const { grupoCandidato, ...resto } = t
  return { ...resto, reclamable: t.asignado === null && grupoCandidato !== null }
}

/** Arma el contexto que necesita el formulario de cada tipo de tarea. */
function contextoDe(t: TareaMock): ContextoTarea {
  const solicitud = solicitudes[t.cursoId]
  const versiones = propuestas.filter((p) => p.cursoId === t.cursoId)
  const vigente = versiones.at(-1)
  switch (t.taskDefinitionKey) {
    case 'Propuesta':
      return { solicitud }
    case 'Revisar':
      return { solicitud, propuestaVigente: vigente, observacionesEmpresa: vigente?.observacionesEmpresa }
    case 'Archivar':
      return { motivoArchivo: motivosArchivo[t.cursoId] ?? 'RECHAZADA' }
    default:
      return {}
  }
}

/** La tarea existe y está asignada al usuario de la sesión: se puede completar. */
function tareaParaCompletar(taskId: string, usuarioId: string) {
  const tarea = tareas.find((t) => t.taskId === taskId)
  if (!tarea) return { respuesta: error(404, 'NO_ENCONTRADO', 'La tarea no existe o ya fue completada.') }
  if (tarea.asignado !== usuarioId) {
    return { respuesta: error(403, 'SIN_PERMISO', 'Solo la persona asignada puede completar la tarea.') }
  }
  return { tarea }
}

/** Simula que el motor completa la tarea: desaparece de la bandeja. */
function quitarTarea(taskId: string) {
  tareas.splice(
    tareas.findIndex((t) => t.taskId === taskId),
    1,
  )
  guardarDatos()
}

function pagina<T>(items: T[]) {
  return { items, total: items.length, page: 0, size: items.length }
}

// Idempotencia de POST /solicitudes: clave -> { cuerpo enviado, respuesta dada }
const solicitudesPorClave = new Map<string, { cuerpo: string; respuesta: SolicitudCreada }>()

export const handlers = [
  // --- Sesiones -------------------------------------------------------------

  http.post(`${API}/sesiones/personal`, async ({ request }) => {
    const { usuarioId, clave } = (await request.json()) as { usuarioId: string; clave: string }
    const usuario = personal.find((p) => p.usuarioId === usuarioId)

    if (!usuario || clave !== CLAVE_DEMO) {
      return error(401, 'NO_AUTENTICADO', 'Usuario o contraseña incorrectos.')
    }

    const sesion: Sesion = { tipo: 'PERSONAL', ...usuario }
    guardarSesion(sesion)
    return HttpResponse.json(sesion)
  }),

  http.post(`${API}/sesiones/empresa`, async ({ request }) => {
    const { usuario, clave } = (await request.json()) as { usuario: string; clave: string }
    const cuenta = cuentasEmpresa.find((c) => c.usuario === usuario)

    if (!cuenta || clave !== CLAVE_DEMO) {
      return error(401, 'NO_AUTENTICADO', 'Usuario o contraseña incorrectos.')
    }

    const sesion: Sesion = { tipo: 'EMPRESA', empresaId: cuenta.empresaId, nombre: cuenta.nombre }
    guardarSesion(sesion)
    return HttpResponse.json(sesion)
  }),

  http.get(`${API}/sesiones/actual`, () => {
    const sesion = leerSesion()
    return sesion ? HttpResponse.json(sesion) : error(401, 'NO_AUTENTICADO', 'No hay sesión iniciada.')
  }),

  http.delete(`${API}/sesiones/actual`, () => {
    guardarSesion(null)
    return new HttpResponse(null, { status: 204 })
  }),

  // --- Solicitud pública ----------------------------------------------------

  http.post(`${API}/solicitudes`, async ({ request }) => {
    const clave = request.headers.get('Idempotency-Key')
    if (!clave) return error(400, 'VALIDACION', 'Falta el header Idempotency-Key.')

    const texto = await request.text()
    const previa = solicitudesPorClave.get(clave)
    if (previa) {
      // Reintento: misma clave y mismos datos -> misma respuesta; datos distintos -> 409
      return previa.cuerpo === texto
        ? HttpResponse.json(previa.respuesta, { status: 201 })
        : error(409, 'ESTADO_INCOMPATIBLE', 'La clave ya se usó con otros datos.')
    }

    const s = JSON.parse(texto) as NuevaSolicitud
    if (!s.empresa?.nombre || !s.contacto?.email || !s.tematica || s.cantidadEstimadaParticipantes < 1) {
      return error(400, 'VALIDACION', 'Faltan datos obligatorios de la solicitud.')
    }

    const cursoId = crypto.randomUUID()
    const respuesta: SolicitudCreada = {
      cursoId,
      processInstanceId: `pi-mock-${cursos.length + 1}`,
      businessKey: `curso:${cursoId}`,
    }
    cursos.push({
      cursoId,
      processInstanceId: respuesta.processInstanceId,
      empresaNombre: s.empresa.nombre,
      tematica: s.tematica,
      estado: 'EN_ACUERDO',
      creadoEn: new Date().toISOString(),
    })
    solicitudesPorClave.set(clave, { cuerpo: texto, respuesta })
    guardarDatos()

    return HttpResponse.json(respuesta, { status: 201 })
  }),

  // --- Tareas ---------------------------------------------------------------

    http.get(`${API}/tareas`, ({ request }) => {
      const sesion = leerSesion()
      if (sesion?.tipo !== 'PERSONAL') return error(401, 'NO_AUTENTICADO', 'Iniciá sesión.')

      const bandeja = new URL(request.url).searchParams.get('bandeja')
      const resultado =
        bandeja === 'disponibles'
          ? tareas.filter((t) => t.asignado === null && t.grupoCandidato !== null && sesion.grupos.includes(t.grupoCandidato))
          : tareas.filter((t) => t.asignado === sesion.usuarioId)

      return HttpResponse.json(pagina(resultado.map(aResumen)))
    }),

    http.post(`${API}/tareas/:taskId/reclamo`, ({ params }) => {
      const sesion = leerSesion()
      if (sesion?.tipo !== 'PERSONAL') return error(401, 'NO_AUTENTICADO', 'Iniciá sesión.')

      const tarea = tareas.find((t) => t.taskId === params.taskId)
      if (!tarea) return error(404, 'NO_ENCONTRADO', 'La tarea no existe.')
      if (!tarea.grupoCandidato || !sesion.grupos.includes(tarea.grupoCandidato)) {
        return error(403, 'SIN_PERMISO', 'No sos candidato para esta tarea.')
      }
      if (tarea.asignado !== null) return error(409, 'YA_RECLAMADA', 'Otra persona ya reclamó esta tarea.')

      tarea.asignado = sesion.usuarioId
      guardarDatos()
      return HttpResponse.json(aResumen(tarea))
    }),

    // --- Detalle y completado de tareas -----------------------------------------

    http.get(`${API}/tareas/:taskId`, ({ params }) => {
      const sesion = leerSesion()
      if (sesion?.tipo !== 'PERSONAL') return error(401, 'NO_AUTENTICADO', 'Iniciá sesión.')

      const tarea = tareas.find((t) => t.taskId === params.taskId)
      if (!tarea) return error(404, 'NO_ENCONTRADO', 'La tarea no existe o ya fue completada.')
      const esCandidato = tarea.grupoCandidato !== null && sesion.grupos.includes(tarea.grupoCandidato)
      if (tarea.asignado !== sesion.usuarioId && !esCandidato) {
        return error(403, 'SIN_PERMISO', 'No tenés acceso a esta tarea.')
      }

      return HttpResponse.json({ ...aResumen(tarea), contexto: contextoDe(tarea) })
    }),

    http.post(`${API}/tareas/:taskId/completado`, ({ params }) => {
      const sesion = leerSesion()
      if (sesion?.tipo !== 'PERSONAL') return error(401, 'NO_AUTENTICADO', 'Iniciá sesión.')

      const { tarea, respuesta } = tareaParaCompletar(String(params.taskId), sesion.usuarioId)
      if (!tarea) return respuesta

      quitarTarea(tarea.taskId)
      return HttpResponse.json({ completada: true })
    }),

    // --- Propuestas -------------------------------------------------------------

    http.post(`${API}/tareas/:taskId/propuesta`, async ({ params, request }) => {
      const sesion = leerSesion()
      if (sesion?.tipo !== 'PERSONAL') return error(401, 'NO_AUTENTICADO', 'Iniciá sesión.')

      const { tarea, respuesta } = tareaParaCompletar(String(params.taskId), sesion.usuarioId)
      if (!tarea) return respuesta
      if (tarea.taskDefinitionKey !== 'Propuesta' && tarea.taskDefinitionKey !== 'Revisar') {
        return error(409, 'ESTADO_INCOMPATIBLE', 'Esta tarea no es de propuesta.')
      }

      const datos = (await request.json()) as NuevaPropuesta
      if (!datos.programa || Number(datos.presupuesto?.importe) <= 0 || !datos.fechasPosibles?.length) {
        return error(400, 'VALIDACION', 'Completá programa, presupuesto y al menos una fecha.')
      }

      const version = propuestas.filter((p) => p.cursoId === tarea.cursoId).length + 1
      propuestas.push({
        cursoId: tarea.cursoId,
        version,
        ...datos,
        estado: 'ENVIADA',
        creada: new Date().toISOString(),
      })
      quitarTarea(tarea.taskId)

      return HttpResponse.json(
        { propuestaId: `prop-${tarea.cursoId}-${version}`, propuestaVersion: version, pdfDisponible: true },
        { status: 201 },
      )
    }),

    http.get(`${API}/cursos/:cursoId/propuestas`, ({ params }) => {
      const items = propuestas
        .filter((p) => p.cursoId === params.cursoId)
        .map(({ cursoId: _cursoId, ...resto }) => resto)
      return HttpResponse.json({ items })
    }),

    http.get(`${API}/cursos/:cursoId/propuestas/:version/pdf`, ({ params }) => {
      const p = propuestas.find((x) => x.cursoId === params.cursoId && x.version === Number(params.version))
      if (!p) return error(404, 'NO_ENCONTRADO', 'No existe esa versión de la propuesta.')

      const pdf = generarPdf([
        `Propuesta de capacitacion - version ${p.version}`,
        `Curso: ${params.cursoId}`,
        `Presupuesto: ${p.presupuesto.moneda} ${p.presupuesto.importe}`,
        `Fechas posibles: ${p.fechasPosibles.join(', ')}`,
        'Programa:',
        ...p.programa.split('\n'),
      ])
      return new HttpResponse(pdf, { headers: { 'Content-Type': 'application/pdf' } })
    }),
]
