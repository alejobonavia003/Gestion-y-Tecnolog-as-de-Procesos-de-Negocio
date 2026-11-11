import { infoEstado } from './estados'

/** Etiqueta de color con el estado del curso. */
export function EstadoCurso({ estado, etiqueta }: { estado: string; etiqueta?: string }) {
  const info = infoEstado(estado, etiqueta)
  return <span className={`estado estado-${info.tono}`}>{info.etiqueta}</span>
}
