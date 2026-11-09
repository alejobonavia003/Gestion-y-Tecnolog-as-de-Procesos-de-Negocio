Qué hay en cada parte
src/
├── main.tsx              → arranca la app: AuthProvider + Router
├── router.tsx            → todas las rutas y cuáles están protegidas
├── index.css             → estilos base (cabecera, botones, contenido)
├── api/
│   ├── tipos.ts          → tipos del contrato (Sesion, ErrorApi…)
│   └── cliente.ts        → función api(): todas las llamadas pasan por acá
├── auth/
│   ├── contexto.ts       → define qué guarda la sesión
│   ├── AuthContext.tsx   → al abrir la web pregunta GET /sesiones/actual
│   ├── useAuth.ts        → hook para usar la sesión en cualquier página
│   └── RutaProtegida.tsx → si no tenés sesión del tipo correcto, te manda al login
├── components/
│   └── Layout.tsx        → cabecera con menú según quién esté logueado
└── pages/                → por ahora son páginas de relleno
    ├── Inicio, Solicitar, LoginPersonal, NoEncontrado
    ├── empresa/  LoginEmpresa, MisCursos, DetalleCurso
    └── personal/ Bandeja, DetalleTarea

Las ideas que conviene entender, porque se va a usar todo el tiempo:

api() en cliente.ts: agrega /api/v1 adelante, manda la cookie de sesión y, si el backend responde con error, lanza un ApiError con el codigo del contrato (YA_RECLAMADA, VERSION_DESACTUALIZADA…). Las páginas nunca usan fetch directo, siempre api().
useAuth(): devuelve sesion, que es null, {tipo:'PERSONAL', usuarioId, grupos, esJefe} o {tipo:'EMPRESA', empresaId, nombre}. Con eso decidís qué mostrar.
RutaProtegida: solo mejora la experiencia en la UI. La seguridad real la valida siempre el backend.

Segundo commit:
.env.mock: activa VITE_USAR_MOCKS=true solo cuando corrés dev:mock.
main.tsx: si los mocks están activos, levanta MSW antes de mostrar la app.
mocks/datos.ts: usuarios de prueba. En las próximas etapas acá vamos a sumar cursos y tareas.
mocks/handlers.ts: las respuestas falsas de los 4 endpoints de sesión, con el mismo formato que definimos en ENDPOINTS_WEB.md. Para simular la cookie guarda la sesión en localStorage, así sobrevive a una recarga.
LoginPersonal y LoginEmpresa: formularios que llaman a la API. Si /sesiones/... responde con un 401 muestran “usuario o contraseña incorrectos” y, si es otro error, “no se pudo conectar”. Después del login te mandan a la página a la que intentabas entrar.
Cabecera: muestra un aviso amarillo cuando estás en modo mock, para no confundirte.
