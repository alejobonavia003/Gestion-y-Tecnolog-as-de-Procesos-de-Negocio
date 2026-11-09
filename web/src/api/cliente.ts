import type { ErrorApi } from './tipos'

const BASE = '/api/v1'

/** Error lanzado cuando el backend responde con un status que no es 2xx. */
export class ApiError extends Error {
  status: number
  codigo: string

  constructor(status: number, cuerpo: ErrorApi) {
    super(cuerpo.mensaje)
    this.status = status
    this.codigo = cuerpo.codigo
  }
}

/**
 * Llama a la API del backend.
 * - Siempre manda la cookie de sesión (credentials: 'include').
 * - Si la respuesta no es 2xx lanza ApiError con el código del contrato.
 */
export async function api<T>(ruta: string, opciones: RequestInit = {}): Promise<T> {
  const respuesta = await fetch(BASE + ruta, {
    credentials: 'include',
    ...opciones,
    headers: {
      Accept: 'application/json',
      ...(opciones.body ? { 'Content-Type': 'application/json' } : {}),
      ...opciones.headers,
    },
  })

  if (!respuesta.ok) {
    let cuerpo: ErrorApi = { codigo: 'ERROR', mensaje: `Error ${respuesta.status}` }
    try {
      cuerpo = await respuesta.json()
    } catch {
      // El backend no devolvió JSON (por ejemplo, está apagado y el proxy da 502)
    }
    throw new ApiError(respuesta.status, cuerpo)
  }

  if (respuesta.status === 204) return undefined as T
  return respuesta.json() as Promise<T>
}


/**
 * Descarga un archivo (por ejemplo, un PDF) y lo abre en una pestaña nueva.
 * La pestaña se abre ANTES de la descarga para que el navegador no la bloquee como popup.
 */
export async function abrirArchivo(ruta: string) {
  const pestania = window.open('', '_blank')
  try {
    const respuesta = await fetch(BASE + ruta, { credentials: 'include' })
    if (!respuesta.ok) throw new Error(`Error ${respuesta.status}`)
    const url = URL.createObjectURL(await respuesta.blob())
    if (pestania) pestania.location.href = url
    else window.location.href = url
  } catch (err) {
    pestania?.close()
    throw err
  }
}
