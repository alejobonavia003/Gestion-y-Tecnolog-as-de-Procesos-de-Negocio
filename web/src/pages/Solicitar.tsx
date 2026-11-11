import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, api } from '../api/cliente'
import type { NuevaSolicitud, SolicitudCreada } from '../api/tipos'

const FORM_VACIO = {
  empresaNombre: '',
  empresaIdentificador: '',
  contactoNombre: '',
  contactoEmail: '',
  contactoTelefono: '',
  tematica: '',
  descripcion: '',
  cantidad: '',
  fechasPreferidas: '',
}

type Campos = typeof FORM_VACIO

/** Validaciones del lado de la web (el backend vuelve a validar todo). */
function validar(f: Campos): string[] {
  const errores: string[] = []
  const cantidad = Number(f.cantidad)
  if (!Number.isInteger(cantidad) || cantidad < 1) {
    errores.push('La cantidad de participantes debe ser un número entero mayor a 0.')
  }
  if (f.descripcion.trim().length < 10) {
    errores.push('Contanos un poco más en la descripción (al menos 10 caracteres).')
  }
  return errores
}

function armarSolicitud(f: Campos): NuevaSolicitud {
  return {
    empresa: {
      nombre: f.empresaNombre.trim(),
      ...(f.empresaIdentificador.trim() && { identificador: f.empresaIdentificador.trim() }),
    },
    contacto: {
      nombre: f.contactoNombre.trim(),
      email: f.contactoEmail.trim(),
      ...(f.contactoTelefono.trim() && { telefono: f.contactoTelefono.trim() }),
    },
    tematica: f.tematica.trim(),
    descripcion: f.descripcion.trim(),
    cantidadEstimadaParticipantes: Number(f.cantidad),
    fechasPreferidas: f.fechasPreferidas.trim(),
  }
}

export function Solicitar() {
  const [campos, setCampos] = useState<Campos>(FORM_VACIO)
  const [errores, setErrores] = useState<string[]>([])
  const [enviando, setEnviando] = useState(false)
  const [creada, setCreada] = useState<SolicitudCreada | null>(null)

  // Clave de idempotencia: se genera una vez por formulario. Si el envío falla y se
  // reintenta, se manda la MISMA clave para que el backend no cree dos solicitudes.
  const [clave, setClave] = useState(() => crypto.randomUUID())

  function cambiar(campo: keyof Campos, valor: string) {
    setCampos((anterior) => ({ ...anterior, [campo]: valor }))
  }

  async function enviar(e: FormEvent) {
    e.preventDefault()
    const encontrados = validar(campos)
    setErrores(encontrados)
    if (encontrados.length > 0) return

    setEnviando(true)
    try {
      const respuesta = await api<SolicitudCreada>('/solicitudes', {
        method: 'POST',
        headers: { 'Idempotency-Key': clave },
        body: JSON.stringify(armarSolicitud(campos)),
      })
      setCreada(respuesta)
    } catch (err) {
      if (err instanceof ApiError && err.status === 400) {
        setErrores([err.message])
      } else if (err instanceof ApiError && err.status === 409) {
        setErrores(['Esta solicitud ya fue enviada con otros datos. Recargá la página y volvé a cargarla.'])
      } else {
        setErrores(['No pudimos registrar la solicitud. Probá enviarla de nuevo en unos minutos.'])
      }
    } finally {
      setEnviando(false)
    }
  }

  function nuevaSolicitud() {
    setCampos(FORM_VACIO)
    setCreada(null)
    setErrores([])
    setClave(crypto.randomUUID())
  }

  if (creada) {
    return (
      <section className="tarjeta angosta">
        <h1>¡Solicitud recibida!</h1>
        <p>
          Gracias, <strong>{campos.contactoNombre}</strong>. Registramos la solicitud de{' '}
          <strong>{campos.empresaNombre}</strong>.
        </p>
        <p>
          Vamos a evaluarla y te enviaremos una propuesta con el programa, el presupuesto y las
          fechas posibles a <strong>{campos.contactoEmail}</strong>.
        </p>
        <p className="ayuda">
          Número de solicitud: <code>{creada.cursoId}</code>
        </p>
        <div className="acciones">
          <Link to="/" className="boton secundario">
            Volver al inicio
          </Link>
          <button onClick={nuevaSolicitud}>Cargar otra solicitud</button>
        </div>
      </section>
    )
  }

  return (
    <section className="tarjeta">
      <h1>Solicitar capacitación</h1>
      <p className="ayuda">Completá los datos y te contactamos con una propuesta.</p>

      <form onSubmit={enviar} className="formulario">
        <fieldset>
          <legend>Empresa</legend>
          <label>
            Nombre o razón social *
            <input
              value={campos.empresaNombre}
              onChange={(e) => cambiar('empresaNombre', e.target.value)}
              required
            />
          </label>
          <label>
            CUIT u otro identificador (opcional)
            <input
              value={campos.empresaIdentificador}
              onChange={(e) => cambiar('empresaIdentificador', e.target.value)}
            />
          </label>
        </fieldset>

        <fieldset>
          <legend>Persona de contacto</legend>
          <label>
            Nombre y apellido *
            <input
              value={campos.contactoNombre}
              onChange={(e) => cambiar('contactoNombre', e.target.value)}
              autoComplete="name"
              required
            />
          </label>
          <div className="fila">
            <label>
              Email *
              <input
                type="email"
                value={campos.contactoEmail}
                onChange={(e) => cambiar('contactoEmail', e.target.value)}
                autoComplete="email"
                required
              />
            </label>
            <label>
              Teléfono (opcional)
              <input
                type="tel"
                value={campos.contactoTelefono}
                onChange={(e) => cambiar('contactoTelefono', e.target.value)}
                autoComplete="tel"
              />
            </label>
          </div>
        </fieldset>

        <fieldset>
          <legend>Capacitación</legend>
          <label>
            Temática *
            <input
              value={campos.tematica}
              onChange={(e) => cambiar('tematica', e.target.value)}
              placeholder="Ej.: Atención al cliente, Excel avanzado…"
              required
            />
          </label>
          <label>
            Descripción *
            <textarea
              rows={4}
              value={campos.descripcion}
              onChange={(e) => cambiar('descripcion', e.target.value)}
              placeholder="Qué necesitan aprender, nivel actual del equipo, objetivos…"
              required
            />
          </label>
          <div className="fila">
            <label>
              Cantidad estimada de participantes *
              <input
                type="number"
                min={1}
                step={1}
                value={campos.cantidad}
                onChange={(e) => cambiar('cantidad', e.target.value)}
                required
              />
            </label>
            <label>
              Fechas preferidas *
              <input
                value={campos.fechasPreferidas}
                onChange={(e) => cambiar('fechasPreferidas', e.target.value)}
                placeholder="Ej.: primera semana de diciembre, por la mañana"
                required
              />
            </label>
          </div>
        </fieldset>

        {errores.length > 0 && (
          <ul className="error">
            {errores.map((mensaje) => (
              <li key={mensaje}>{mensaje}</li>
            ))}
          </ul>
        )}

        <button type="submit" disabled={enviando}>
          {enviando ? 'Enviando…' : 'Enviar solicitud'}
        </button>
      </form>
    </section>
  )
}
