import { api } from './cliente'
import type { ActividadesCurso, CursoDetalle, CursoResumen, Pagina } from './tipos'

/** Cursos de la empresa logueada (el backend filtra por la sesión). */
export function listarCursos() {
  return api<Pagina<CursoResumen>>('/cursos')
}

export function obtenerCurso(cursoId: string) {
  return api<CursoDetalle>(`/cursos/${cursoId}`)
}

/** Actividades activas del curso. Durante la preparación puede haber varias a la vez (ramas paralelas). */
export function listarActividades(cursoId: string) {
  return api<ActividadesCurso>(`/cursos/${cursoId}/actividades`)
}
