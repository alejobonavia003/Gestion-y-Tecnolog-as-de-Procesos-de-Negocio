import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import { router } from './router'
import './index.css'

/** Si se arrancó con `npm run dev:mock`, activa los mocks antes de mostrar la app. */
async function activarMocks() {
  if (import.meta.env.VITE_USAR_MOCKS !== 'true') return
  const { worker } = await import('./mocks/browser')
  // 'bypass': lo que no tenga mock (por ejemplo, imágenes) se pide normalmente
  await worker.start({ onUnhandledFrame: 'bypass' })
}

activarMocks().then(() => {
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </StrictMode>,
  )
})
