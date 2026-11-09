import { createBrowserRouter } from 'react-router-dom'
import { RutaProtegida } from './auth/RutaProtegida'
import { Layout } from './components/Layout'
import { Inicio } from './pages/Inicio'
import { LoginPersonal } from './pages/LoginPersonal'
import { NoEncontrado } from './pages/NoEncontrado'
import { Solicitar } from './pages/Solicitar'
import { DetalleCurso } from './pages/empresa/DetalleCurso'
import { LoginEmpresa } from './pages/empresa/LoginEmpresa'
import { MisCursos } from './pages/empresa/MisCursos'
import { Bandeja } from './pages/personal/Bandeja'
import { DetalleTarea } from './pages/personal/DetalleTarea'

export const router = createBrowserRouter([
  {
    element: <Layout />,
    children: [
      // Público
      { path: '/', element: <Inicio /> },
      { path: '/solicitar', element: <Solicitar /> },
      { path: '/login', element: <LoginPersonal /> },
      { path: '/empresa/login', element: <LoginEmpresa /> },

      // Solo empresas logueadas
      {
        element: <RutaProtegida tipo="EMPRESA" />,
        children: [
          { path: '/empresa/cursos', element: <MisCursos /> },
          { path: '/empresa/cursos/:cursoId', element: <DetalleCurso /> },
        ],
      },

      // Solo personal logueado (administración y docentes)
      {
        element: <RutaProtegida tipo="PERSONAL" />,
        children: [
          { path: '/tareas', element: <Bandeja /> },
          { path: '/tareas/:taskId', element: <DetalleTarea /> },
        ],
      },

      { path: '*', element: <NoEncontrado /> },
    ],
  },
])
