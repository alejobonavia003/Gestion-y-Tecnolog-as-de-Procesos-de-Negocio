// Qué se sabe de cada tipo de tarea del BPMN, según su taskDefinitionKey.
// Los IDs salen del modelo (docs/MODELO_Y_INTEGRACION.md#acuerdo-de-integración §C). Si Alejo los cambia, se actualizan acá.

export type DondeSeCompleta = 'web' | 'flowable' | 'app-materiales'

type InfoTarea = {
  titulo: string
  dondeSeCompleta: DondeSeCompleta
}

export const CATALOGO_TAREAS: Record<string, InfoTarea> = {
  // Jefe administrativo (claves reales del BPMN + alias de mocks)
  registrar_elaborar_propuesta: { titulo: 'Registrar y elaborar propuesta', dondeSeCompleta: 'web' },
  Propuesta: { titulo: 'Registrar y elaborar propuesta', dondeSeCompleta: 'web' },
  revisar_propuesta: { titulo: 'Revisar propuesta', dondeSeCompleta: 'web' },
  Revisar: { titulo: 'Revisar propuesta', dondeSeCompleta: 'web' },
  archivar_solicitud: { titulo: 'Archivar solicitud', dondeSeCompleta: 'web' },
  Archivar: { titulo: 'Archivar solicitud', dondeSeCompleta: 'web' },
  archivar_expediente: { titulo: 'Enviar informe y archivar expediente', dondeSeCompleta: 'web' },
  enviar_informe: { titulo: 'Enviar informe y archivar expediente', dondeSeCompleta: 'web' },
  EnviarInforme: { titulo: 'Enviar informe y archivar expediente', dondeSeCompleta: 'web' },
  // Administración (grupo Equipo-Administrativo)
  confeccionar_lista: { titulo: 'Confeccionar lista definitiva', dondeSeCompleta: 'web' },
  Lista: { titulo: 'Confeccionar lista definitiva', dondeSeCompleta: 'web' },
  evaluar_recursos: { titulo: 'Evaluar recursos', dondeSeCompleta: 'web' },
  Activity_12i2hpf: { titulo: 'Evaluar recursos', dondeSeCompleta: 'web' },
  preparar_materiales: { titulo: 'Preparar materiales', dondeSeCompleta: 'app-materiales' },
  Material: { titulo: 'Preparar materiales', dondeSeCompleta: 'app-materiales' },
  reservar_aula_virtual: { titulo: 'Solicitar aula virtual', dondeSeCompleta: 'flowable' },
  Virtual: { titulo: 'Solicitar aula virtual', dondeSeCompleta: 'flowable' },
  contratar_equipamiento: { titulo: 'Contratar equipamiento', dondeSeCompleta: 'flowable' },
  Equipo: { titulo: 'Contratar equipamiento', dondeSeCompleta: 'flowable' },
  liberar_equipamiento: { titulo: 'Liberar equipamiento', dondeSeCompleta: 'flowable' },
  Activity_1m08pcl: { titulo: 'Liberar equipamiento', dondeSeCompleta: 'flowable' },
  liberar_aula_virtual: { titulo: 'Liberar aula virtual', dondeSeCompleta: 'flowable' },
  // Docentes (Equipo-Docente / Jefe-Docente)
  definir_docente: { titulo: 'Definir docente', dondeSeCompleta: 'web' },
  Activity_1018bdz: { titulo: 'Definir docente', dondeSeCompleta: 'web' },
  desarrollar_curso: { titulo: 'Desarrollar curso', dondeSeCompleta: 'web' },
  Dictar: { titulo: 'Desarrollar curso', dondeSeCompleta: 'web' },
  elaborar_informe: { titulo: 'Elaborar informe de resultados', dondeSeCompleta: 'flowable' },
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
