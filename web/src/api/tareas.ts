import { api } from './cliente'
import type { Pagina, TareaResumen } from './tipos'

export type Bandeja = 'asignadas' | 'disponibles'

export function listarTareas(bandeja: Bandeja) {
  return api<Pagina<TareaResumen>>(`/tareas?bandeja=${bandeja}`)
}

export function reclamarTarea(taskId: string) {
  return api<TareaResumen>(`/tareas/${taskId}/reclamo`, { method: 'POST', body: '{}' })
}
