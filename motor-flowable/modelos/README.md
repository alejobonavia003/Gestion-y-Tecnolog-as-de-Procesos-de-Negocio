# Modelos del proceso

## Cargar la app en tu Flowable (una vez, y cada vez que cambie)
1. Flowable UI → Modeler → Apps → **Import App** → elegir `organizacion-cursos.zip`.
   Si pregunta, aceptar reemplazar los modelos existentes.
2. Abrir la app → **Publish**. Esto la despliega en el motor.
3. Verificar:
   curl -s -u admin:test "http://localhost:8080/flowable-ui/process-api/repository/process-definitions?key=process&latest=true"

## Al modificar el modelo (Alejo)
1. Editar en el Modeler y publicar.
2. Volver a exportar la app (.zip) y los XML (.bpmn20.xml y .dmn).
3. Reemplazar los archivos de esta carpeta y commitear.
4. Avisar al equipo para que reimporten y publiquen.