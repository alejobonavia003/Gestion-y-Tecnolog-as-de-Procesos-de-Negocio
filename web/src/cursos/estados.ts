// Estados del curso que ve la empresa (docs/ACUERDO_DE_INTEGRACION.md §E y enunciado §4.1.2).
// El backend los calcula a partir de Flowable; la web solo los muestra.

type InfoEstado = {
  etiqueta: string
  tono: 'info' | 'progreso' | 'exito' | 'neutro' | 'alerta'
}

export const ESTADOS: Record<string, InfoEstado> = {
  EN_ACUERDO: { etiqueta: 'En evaluación o acuerdo', tono: 'neutro' },
  EN_PREPARACION: { etiqueta: 'En preparación', tono: 'info' },
  CONFIRMADO_PENDIENTE_INICIO: { etiqueta: 'Confirmado y pendiente de inicio', tono: 'info' },
  EN_DESARROLLO: { etiqueta: 'En desarrollo', tono: 'progreso' },
  EN_ELABORACION_INFORME: { etiqueta: 'En elaboración del informe', tono: 'progreso' },
  EN_CIERRE: { etiqueta: 'Cierre en curso', tono: 'progreso' },
  CANCELACION_EN_CURSO: { etiqueta: 'Cancelación en curso', tono: 'alerta' },
  FINALIZADO: { etiqueta: 'Finalizado', tono: 'exito' },
  CANCELADO: { etiqueta: 'Cancelado', tono: 'alerta' },
}

/** Etapas que se muestran en la línea de progreso del curso, en orden. */
export const ETAPAS = [
  'EN_PREPARACION',
  'CONFIRMADO_PENDIENTE_INICIO',
  'EN_DESARROLLO',
  'EN_ELABORACION_INFORME',
  'FINALIZADO',
]

export function infoEstado(estado: string, etiquetaBackend?: string): InfoEstado {
  return ESTADOS[estado] ?? { etiqueta: etiquetaBackend ?? estado, tono: 'neutro' }
}

/** Posición del estado en la línea de progreso (EN_CIERRE cuenta como la última etapa en curso). */
export function indiceEtapa(estado: string) {
  if (estado === 'EN_CIERRE') return ETAPAS.indexOf('EN_ELABORACION_INFORME')
  return ETAPAS.indexOf(estado)
}
