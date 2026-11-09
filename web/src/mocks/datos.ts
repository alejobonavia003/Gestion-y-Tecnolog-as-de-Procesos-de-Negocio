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
]

export const tareas: TareaMock[] = cargar('mock-tareas', TAREAS_INICIALES)
