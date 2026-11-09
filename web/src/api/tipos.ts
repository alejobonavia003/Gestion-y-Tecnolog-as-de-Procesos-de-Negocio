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

// --- Solicitud pública (POST /solicitudes) ---

export type NuevaSolicitud = {
  empresa: { nombre: string; identificador?: string }
  contacto: { nombre: string; email: string; telefono?: string }
  tematica: string
  descripcion: string
  cantidadEstimadaParticipantes: number
  fechasPreferidas: string
}

export type SolicitudCreada = {
  cursoId: string
  processInstanceId: string
  businessKey: string
}

// --- Tareas (GET /tareas, POST /tareas/{id}/reclamo) ---

export type Pagina<T> = {
  items: T[]
  total: number
  page: number
  size: number
}

export type TareaResumen = {
  taskId: string
  taskDefinitionKey: string
  nombre: string
  cursoId: string
  cursoNombre: string
  empresaNombre: string
  creada: string // fecha ISO
  asignado: string | null // null = tarea de grupo sin reclamar
  reclamable: boolean
}
