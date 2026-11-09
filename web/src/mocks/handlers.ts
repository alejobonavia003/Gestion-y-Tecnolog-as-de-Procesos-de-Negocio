import { http, HttpResponse } from 'msw/http'
import type { NuevaSolicitud, Sesion, SolicitudCreada } from '../api/tipos'
import { CLAVE_DEMO, cuentasEmpresa, cursos, guardarDatos, personal, tareas, type TareaMock } from './datos'

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
]
