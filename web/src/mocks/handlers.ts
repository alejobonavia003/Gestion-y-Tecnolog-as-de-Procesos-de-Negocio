import { http, HttpResponse } from 'msw/http'
import type { NuevaSolicitud, Sesion, SolicitudCreada } from '../api/tipos'
import { CLAVE_DEMO, cuentasEmpresa, cursos, personal } from './datos'

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

    return HttpResponse.json(respuesta, { status: 201 })
  }),
]
