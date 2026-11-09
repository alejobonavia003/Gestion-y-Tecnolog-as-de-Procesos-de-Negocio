import { useParams } from 'react-router-dom'

export function DetalleCurso() {
  const { cursoId } = useParams()
  return (
    <section>
      <h1>Curso {cursoId}</h1>
      <p>Estado del curso y actividades en curso.</p>
    </section>
  )
}
