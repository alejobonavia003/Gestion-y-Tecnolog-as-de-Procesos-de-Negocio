// Funciones de formato compartidas por las pantallas de tareas.

export function formatearFecha(iso: string) {
  return new Date(iso).toLocaleString('es-AR', { dateStyle: 'short', timeStyle: 'short' })
}

export function formatearImporte(importe: string, moneda: string) {
  return `${moneda} ${Number(importe).toLocaleString('es-AR', { minimumFractionDigits: 2 })}`
}

/**
 * Convierte el valor de un <input type="datetime-local"> ("2026-12-01T09:00")
 * a ISO con el offset de Argentina ("2026-12-01T09:00:00-03:00").
 * Argentina no tiene horario de verano, así que el offset es fijo.
 */
export function localAIso(valor: string) {
  return `${valor}:00-03:00`
}

/** Inverso de localAIso: de ISO a valor para <input type="datetime-local">, en hora de Argentina. */
export function isoALocal(iso: string) {
  const fecha = new Date(new Date(iso).getTime() - 3 * 60 * 60 * 1000)
  return fecha.toISOString().slice(0, 16)
}
