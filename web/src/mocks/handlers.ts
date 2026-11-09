import { http, HttpResponse } from 'msw/http'
import type { Sesion } from '../api/tipos'
import { CLAVE_DEMO, cuentasEmpresa, personal } from './datos'

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
]
