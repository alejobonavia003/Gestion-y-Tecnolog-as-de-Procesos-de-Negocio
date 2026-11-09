// Qué se sabe de cada tipo de tarea del BPMN, según su taskDefinitionKey.
// Los IDs salen del modelo (docs/ACUERDO_DE_INTEGRACION.md §C). Si Alejo los cambia, se actualizan acá.

export type DondeSeCompleta = 'web' | 'flowable' | 'app-materiales'

type InfoTarea = {
  titulo: string
  dondeSeCompleta: DondeSeCompleta
}

export const CATALOGO_TAREAS: Record<string, InfoTarea> = {
  // Jefe administrativo
  Propuesta: { titulo: 'Registrar y elaborar propuesta', dondeSeCompleta: 'web' },
  Revisar: { titulo: 'Revisar propuesta', dondeSeCompleta: 'web' },
  Archivar: { titulo: 'Archivar solicitud', dondeSeCompleta: 'web' },
  EnviarInforme: { titulo: 'Enviar informe y archivar expediente', dondeSeCompleta: 'web' },
  // Administración (grupo)
  Lista: { titulo: 'Confeccionar lista definitiva', dondeSeCompleta: 'web' },
  Activity_12i2hpf: { titulo: 'Evaluar recursos', dondeSeCompleta: 'web' },
  Material: { titulo: 'Preparar materiales', dondeSeCompleta: 'app-materiales' },
  Virtual: { titulo: 'Solicitar aula virtual', dondeSeCompleta: 'flowable' },
  Equipo: { titulo: 'Contratar equipamiento', dondeSeCompleta: 'flowable' },
  Activity_1m08pcl: { titulo: 'Liberar equipamiento', dondeSeCompleta: 'flowable' },
  // Docentes
  Activity_1018bdz: { titulo: 'Definir docente', dondeSeCompleta: 'web' },
  Dictar: { titulo: 'Desarrollar curso', dondeSeCompleta: 'web' },
  Informe: { titulo: 'Elaborar informe de resultados', dondeSeCompleta: 'flowable' },
}

export const TEXTO_DONDE: Record<DondeSeCompleta, string> = {
  web: '',
  flowable: 'Se completa desde Flowable',
  'app-materiales': 'Se completa desde la app de materiales',
}

export function infoTarea(taskDefinitionKey: string): InfoTarea {
  return CATALOGO_TAREAS[taskDefinitionKey] ?? { titulo: taskDefinitionKey, dondeSeCompleta: 'flowable' }
}
