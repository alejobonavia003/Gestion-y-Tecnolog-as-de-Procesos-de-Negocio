import { useParams } from 'react-router-dom'

export function DetalleTarea() {
  const { taskId } = useParams()
  return (
    <section>
      <h1>Tarea {taskId}</h1>
      <p>Acá se muestra el formulario según el tipo de tarea.</p>
    </section>
  )
}
