// Tipos compartidos con el backend (ver ENDPOINTS_WEB.md)

export type SesionPersonal = {
  tipo: 'PERSONAL'
  usuarioId: string
  nombre?: string
  grupos: string[] // 'administracion' | 'docentes'
  esJefe: boolean
}

export type SesionEmpresa = {
  tipo: 'EMPRESA'
  empresaId: string
  nombre: string
}

export type Sesion = SesionPersonal | SesionEmpresa

export type ErrorApi = {
  codigo: string
  mensaje: string
}
