import type { Actividad, ContextoTarea, CursoDetalle, Participante, Propuesta } from '../api/tipos'
// Datos de prueba para los mocks. Simulan lo que tendrán Flowable y la BD institucional.
// Las contraseñas son solo para desarrollo local; en el sistema real valida Flowable.

export const CLAVE_DEMO = '1234'

// Los datos que cambian (cursos, tareas) se guardan en localStorage para que no se
// pierdan al recargar ni al cambiar de usuario. Para volver a los datos iniciales,
// en la consola del navegador: localStorage.clear() y recargar.
function cargar<T>(clave: string, inicial: T): T {
  const guardado = localStorage.getItem(clave)
  return guardado ? (JSON.parse(guardado) as T) : structuredClone(inicial)
}

export function guardarDatos() {
  localStorage.setItem('mock-cursos', JSON.stringify(cursos))
  localStorage.setItem('mock-tareas', JSON.stringify(tareas))
  localStorage.setItem('mock-propuestas', JSON.stringify(propuestas))
  localStorage.setItem('mock-cursos-portal', JSON.stringify(cursosPortal))
}

export const personal = [
  { usuarioId: 'admin_jefe', nombre: 'Jefa Administrativa', grupos: ['administracion'], esJefe: true },
  { usuarioId: 'admin_01', nombre: 'Administrativo Uno', grupos: ['administracion'], esJefe: false },
  { usuarioId: 'docente_jefe', nombre: 'Jefe Docente', grupos: ['docentes'], esJefe: true },
  { usuarioId: 'docente_01', nombre: 'Docente Uno', grupos: ['docentes'], esJefe: false },
  { usuarioId: 'docente_02', nombre: 'Docente Dos', grupos: ['docentes'], esJefe: false },
]

export const cuentasEmpresa = [
  { usuario: 'empresa-demo', empresaId: 'emp-1', nombre: 'Empresa Demo S.A.' },
  { usuario: 'otra-empresa', empresaId: 'emp-2', nombre: 'Otra Empresa SRL' },
]

// Cursos creados desde el formulario público (se pierden al recargar la página)
export type CursoMock = {
  cursoId: string
  processInstanceId: string
  empresaNombre: string
  tematica: string
  estado: string
  creadoEn: string
}

export const cursos: CursoMock[] = cargar('mock-cursos', [])


// Tareas de prueba. Simulan lo que devolvería Flowable.
// - asignado con valor: tarea directa (no se reclama)
// - asignado null + grupoCandidato: tarea de grupo libre (se reclama)
export type TareaMock = {
  taskId: string
  taskDefinitionKey: string
  nombre: string
  cursoId: string
  cursoNombre: string
  empresaNombre: string
  creada: string
  asignado: string | null
  grupoCandidato: string | null
}

const TAREAS_INICIALES: TareaMock[] = [
  {
    taskId: 't-1', taskDefinitionKey: 'Propuesta', nombre: 'Registrar y elaborar propuesta',
    cursoId: 'c-1', cursoNombre: 'Excel avanzado', empresaNombre: 'Empresa Demo S.A.',
    creada: '2026-11-09T09:00:00-03:00', asignado: 'admin_jefe', grupoCandidato: null,
  },
  {
    taskId: 't-2', taskDefinitionKey: 'Archivar', nombre: 'Archivar solicitud',
    cursoId: 'c-2', cursoNombre: 'Oratoria', empresaNombre: 'Otra Empresa SRL',
    creada: '2026-11-08T15:30:00-03:00', asignado: 'admin_jefe', grupoCandidato: null,
  },
  {
    taskId: 't-3', taskDefinitionKey: 'Activity_12i2hpf', nombre: 'Evaluar recursos',
    cursoId: 'c-3', cursoNombre: 'Atención al cliente', empresaNombre: 'Empresa Demo S.A.',
    creada: '2026-11-09T10:15:00-03:00', asignado: null, grupoCandidato: 'administracion',
  },
  {
    taskId: 't-4', taskDefinitionKey: 'Material', nombre: 'Preparar materiales',
    cursoId: 'c-3', cursoNombre: 'Atención al cliente', empresaNombre: 'Empresa Demo S.A.',
    creada: '2026-11-09T10:15:00-03:00', asignado: null, grupoCandidato: 'administracion',
  },
  {
    taskId: 't-5', taskDefinitionKey: 'Lista', nombre: 'Confeccionar lista definitiva',
    cursoId: 'c-3', cursoNombre: 'Atención al cliente', empresaNombre: 'Empresa Demo S.A.',
    creada: '2026-11-09T11:00:00-03:00', asignado: 'admin_01', grupoCandidato: 'administracion',
  },
  {
    taskId: 't-6', taskDefinitionKey: 'Activity_1018bdz', nombre: 'Definir docente',
    cursoId: 'c-4', cursoNombre: 'Seguridad e higiene', empresaNombre: 'Otra Empresa SRL',
    creada: '2026-11-07T12:00:00-03:00', asignado: 'docente_jefe', grupoCandidato: null,
  },
  {
    taskId: 't-7', taskDefinitionKey: 'Dictar', nombre: 'Desarrollar curso',
    cursoId: 'c-5', cursoNombre: 'Liderazgo', empresaNombre: 'Empresa Demo S.A.',
    creada: '2026-11-06T09:00:00-03:00', asignado: 'docente_01', grupoCandidato: null,
  },
  {
    taskId: 't-8', taskDefinitionKey: 'Revisar', nombre: 'Revisar propuesta',
    cursoId: 'c-6', cursoNombre: 'Ventas consultivas', empresaNombre: 'Otra Empresa SRL',
    creada: '2026-11-09T12:30:00-03:00', asignado: 'admin_jefe', grupoCandidato: null,
  },
  {
    taskId: 't-9', taskDefinitionKey: 'EnviarInforme', nombre: 'Enviar informe a empresa y archivar expediente',
    cursoId: 'c-12', cursoNombre: 'Gestión del tiempo', empresaNombre: 'Empresa Demo S.A.',
    creada: '2026-11-09T16:00:00-03:00', asignado: 'admin_jefe', grupoCandidato: null,
  },
]

export const tareas: TareaMock[] = cargar('mock-tareas', TAREAS_INICIALES)

// Datos de la solicitud original de cada curso (los usa el contexto de las tareas)
export const solicitudes: Record<string, NonNullable<ContextoTarea['solicitud']>> = {
  'c-1': {
    tematica: 'Excel avanzado',
    descripcion: 'Tablas dinámicas, fórmulas avanzadas y macros para el área contable.',
    cantidadEstimadaParticipantes: 12,
    fechasPreferidas: 'Primera semana de diciembre, por la mañana',
  },
  'c-6': {
    tematica: 'Ventas consultivas',
    descripcion: 'Técnicas de venta consultiva para el equipo comercial.',
    cantidadEstimadaParticipantes: 8,
    fechasPreferidas: 'Martes y jueves por la tarde',
  },
}

// Motivo de archivo de las tareas "Archivar solicitud" (por curso)
export const motivosArchivo: Record<string, 'RECHAZADA' | 'SIN_RESPUESTA'> = {
  'c-2': 'SIN_RESPUESTA',
}

// Propuestas emitidas, con todas sus versiones
export type PropuestaMock = Propuesta & { cursoId: string }

const PROPUESTAS_INICIALES: PropuestaMock[] = [
  {
    cursoId: 'c-6',
    version: 1,
    programa: 'Módulo 1: escucha activa.\nMódulo 2: detección de necesidades.\nMódulo 3: cierre.',
    presupuesto: { importe: '200000.00', moneda: 'ARS' },
    fechasPosibles: ['2026-12-02T14:00:00-03:00', '2026-12-04T14:00:00-03:00'],
    estado: 'MODIFICACIONES',
    observacionesEmpresa: 'El presupuesto nos queda alto. ¿Se puede reducir a 6 horas y bajar el costo?',
    creada: '2026-11-05T10:00:00-03:00',
  },
  {
    cursoId: 'c-3',
    version: 1,
    programa: 'Módulo 1: comunicación con el cliente.\nMódulo 2: manejo de reclamos.',
    presupuesto: { importe: '120000.00', moneda: 'ARS' },
    fechasPosibles: ['2026-11-25T09:00:00-03:00'],
    estado: 'ACEPTADA',
    creada: '2026-11-03T11:00:00-03:00',
  },
]

export const propuestas: PropuestaMock[] = cargar('mock-propuestas', PROPUESTAS_INICIALES)


// Nómina enviada por la empresa (la usa la tarea "Confeccionar lista definitiva")
export const nominas: Record<string, Participante[]> = {
  'c-3': [
    { nombre: 'Lucía Fernández' },
    { nombre: 'Martín Gómez' },
    { nombre: 'Sofía Rodríguez' },
    { nombre: 'Juan Pérez' },
    { nombre: 'Valentina López' },
  ],
}


// Cursos que ve cada empresa en su portal (empresaId sale de cuentasEmpresa)
export type CursoPortalMock = CursoDetalle & { empresaId: string }

const CURSOS_PORTAL_INICIALES: CursoPortalMock[] = [
  {
    cursoId: 'c-3', empresaId: 'emp-1', nombre: 'Atención al cliente', estado: 'EN_PREPARACION',
    estadoEtiqueta: 'En preparación', fechaCurso: '2026-11-25T09:00:00-03:00',
    descripcion: 'Comunicación y manejo de reclamos para el equipo de atención.', cantidadParticipantes: 5,
  },
  {
    cursoId: 'c-5', empresaId: 'emp-1', nombre: 'Liderazgo', estado: 'EN_DESARROLLO',
    estadoEtiqueta: 'En desarrollo', fechaCurso: '2026-11-06T09:00:00-03:00',
    descripcion: 'Liderazgo de equipos para mandos medios.', cantidadParticipantes: 10,
  },
  {
    cursoId: 'c-7', empresaId: 'emp-1', nombre: 'Seguridad informática', estado: 'FINALIZADO',
    estadoEtiqueta: 'Finalizado', fechaCurso: '2026-10-01T09:00:00-03:00',
    descripcion: 'Buenas prácticas de seguridad para usuarios.', cantidadParticipantes: 20,
  },
  {
    cursoId: 'c-4', empresaId: 'emp-2', nombre: 'Seguridad e higiene', estado: 'CONFIRMADO_PENDIENTE_INICIO',
    estadoEtiqueta: 'Confirmado y pendiente de inicio', fechaCurso: '2026-12-10T08:00:00-03:00',
    descripcion: 'Normativa y prevención de riesgos.', cantidadParticipantes: 15,
  },
  {
    cursoId: 'c-9', empresaId: 'emp-2', nombre: 'Primeros auxilios', estado: 'EN_PREPARACION',
    estadoEtiqueta: 'En preparación', fechaCurso: '2026-12-15T09:00:00-03:00',
    descripcion: 'RCP y primeros auxilios básicos.', cantidadParticipantes: 12,
  },
  {
    cursoId: 'c-10', empresaId: 'emp-2', nombre: 'Excel inicial', estado: 'CANCELADO',
    estadoEtiqueta: 'Cancelado', fechaCurso: null,
    descripcion: 'Introducción a planillas de cálculo.', cantidadParticipantes: 6,
  },
  {
    cursoId: 'c-12', empresaId: 'emp-1', nombre: 'Gestión del tiempo', estado: 'EN_CIERRE',
    estadoEtiqueta: 'Cierre en curso', fechaCurso: '2026-10-20T09:00:00-03:00',
    descripcion: 'Organización personal y priorización de tareas.', cantidadParticipantes: 14,
  },
]

export const cursosPortal: CursoPortalMock[] = cargar('mock-cursos-portal', CURSOS_PORTAL_INICIALES)

// Esperas de mensajes activas por curso (por ejemplo, la nómina que todavía no mandó la empresa)
export const esperas: Record<string, Actividad[]> = {
  'c-9': [{ activityId: 'Nomina', nombre: 'Recibir nómina de participantes', tipo: 'ESPERA_MENSAJE' }],
}


// Informes de resultados ya elaborados por el jefe docente
export const informes: Record<string, { resumen: string; pdfDisponible: boolean }> = {
  'c-12': {
    resumen:
      'Asistieron 13 de 14 participantes. Se cumplieron todos los módulos. ' +
      'La evaluación final tuvo un promedio de 8,4 y la encuesta de satisfacción, 4,6 sobre 5.',
    pdfDisponible: true,
  },
}
