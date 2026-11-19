import { abrirArchivo, api } from './cliente'
import type { NuevaPropuesta, Pagina, Propuesta, PropuestaCreada, TareaDetalle, TareaResumen } from './tipos'

export type Bandeja = 'asignadas' | 'disponibles'

export function listarTareas(bandeja: Bandeja) {
  return api<Pagina<TareaResumen>>(`/tareas?bandeja=${bandeja}`)
}

export function reclamarTarea(taskId: string) {
  return api<TareaResumen>(`/tareas/${taskId}/reclamo`, { method: 'POST', body: '{}' })
}

export function obtenerTarea(taskId: string) {
  return api<TareaDetalle>(`/tareas/${taskId}`)
}

/** Completa una tarea simple. `datos` depende del tipo de tarea (ver docs/BACKEND.md (contrato de endpoints) #12). */
export function completarTarea(taskId: string, datos: Record<string, unknown> = {}) {
  return api<void>(`/tareas/${taskId}/completado`, { method: 'POST', body: JSON.stringify({ datos }) })
}

/** Registrar/elaborar o revisar propuesta: genera una versión nueva con su PDF y completa la tarea. */
export function enviarPropuesta(taskId: string, propuesta: NuevaPropuesta) {
  return api<PropuestaCreada>(`/tareas/${taskId}/propuesta`, {
    method: 'POST',
    body: JSON.stringify(propuesta),
  })
}

export function listarPropuestas(cursoId: string) {
  return api<{ items: Propuesta[] }>(`/cursos/${cursoId}/propuestas`)
}

export function abrirPdfPropuesta(cursoId: string, version: number) {
  return abrirArchivo(`/cursos/${cursoId}/propuestas/${version}/pdf`)
}


/** Definir docente: el jefe docente elige uno de los tres (puede ser él mismo). */
export function definirDocente(taskId: string, docenteId: string) {
  return api<void>(`/tareas/${taskId}/docente`, { method: 'POST', body: JSON.stringify({ docenteId }) })
}

/** Desarrollar curso: el docente asignado registra que terminó el dictado. */
export function registrarFinDictado(taskId: string) {
  return api<void>(`/tareas/${taskId}/finalizacion-dictado`, { method: 'POST', body: '{}' })
}

export function abrirPdfInforme(cursoId: string) {
  return abrirArchivo(`/cursos/${cursoId}/informe/pdf`)
}
