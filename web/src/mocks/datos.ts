// Datos de prueba para los mocks. Simulan lo que tendrán Flowable y la BD institucional.
// Las contraseñas son solo para desarrollo local; en el sistema real valida Flowable.

export const CLAVE_DEMO = '1234'

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

export const cursos: CursoMock[] = []
