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

// --- Detalle de tarea y propuestas (etapa 6) ---

export type Presupuesto = {
  importe: string // decimal como texto, ej. "150000.00"
  moneda: string // ej. "ARS"
}

export type DatosSolicitud = {
  tematica: string
  descripcion: string
  cantidadEstimadaParticipantes: number
  fechasPreferidas: string
}

export type Propuesta = {
  version: number
  programa: string
  presupuesto: Presupuesto
  fechasPosibles: string[] // fechas ISO con offset
  estado: string // ENVIADA | MODIFICACIONES | ACEPTADA | RECHAZADA
  observacionesEmpresa?: string
  creada: string
}

export type Participante = {
  nombre: string
}

/** Datos extra que trae GET /tareas/{taskId} según el tipo de tarea. */
export type ContextoTarea = {
  solicitud?: DatosSolicitud
  propuestaVigente?: Propuesta
  observacionesEmpresa?: string
  motivoArchivo?: 'RECHAZADA' | 'SIN_RESPUESTA'
  participantes?: Participante[] // nómina enviada por la empresa (tarea Lista)
}

export type TareaDetalle = TareaResumen & {
  contexto: ContextoTarea
}

export type NuevaPropuesta = {
  programa: string
  presupuesto: Presupuesto
  fechasPosibles: string[]
}

export type PropuestaCreada = {
  propuestaId: string
  propuestaVersion: number
  pdfDisponible: boolean
}
