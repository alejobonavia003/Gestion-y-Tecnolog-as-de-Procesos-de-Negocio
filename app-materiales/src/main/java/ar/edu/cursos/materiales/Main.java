package ar.edu.cursos.materiales;

import java.nio.file.Path;
import javax.swing.*;
import java.awt.GraphicsEnvironment;

public final class Main {
    public static void main(String[] args) {
        try {
            Path archivo=null; boolean diagnostico=false; boolean analisis=false;
            for(int i=0;i<args.length;i++) {
                if("--config".equals(args[i]) && i+1<args.length) archivo=Path.of(args[++i]);
                else if("--diagnostico".equals(args[i])) diagnostico=true;
                else if("--analisis".equals(args[i])) analisis=true;
                else throw new IllegalArgumentException("Uso: java -jar app-materiales.jar [--config archivo] [--diagnostico] [--analisis]");
            }
            Configuracion config=Configuracion.cargar(archivo);
            if(analisis) {
                try(Motor m=new Motor(config)) {
                    var reporte=new AnalisisHistoricoService(m.engine(),config).generarReporte();
                    System.out.println(reporte.formatearConsola());
                }
            } else if(diagnostico) {
                try(Motor m=new Motor(config)) {
                    var d=m.engine().getRepositoryService().createProcessDefinitionQuery().processDefinitionKey(config.proceso()).latestVersion().singleResult();
                    if(d==null) throw new ErrorAplicacion("No está publicado el proceso configurado.");
                    System.out.println("Conexión Java OK. Definición: "+d.getId());
                    var tasks=m.engine().getTaskService().createTaskQuery().processDefinitionKey(config.proceso()).active().listPage(0,20);
                    for(var t:tasks) System.out.println("Tarea: "+t.getId()+" | "+t.getTaskDefinitionKey()+" | instancia: "+t.getProcessInstanceId());
                    System.out.println("Diagnóstico de lectura finalizado; no se iniciaron ni completaron instancias.");
                }
            } else {
                if(GraphicsEnvironment.isHeadless()) throw new ErrorAplicacion("No hay entorno gráfico. Usá --analisis o --diagnostico por consola.");
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                SwingUtilities.invokeLater(()->new Ventana(config).setVisible(true));
            }
        } catch(Exception e) {
            String mensaje=e instanceof IllegalArgumentException || e instanceof java.io.IOException ? "Revisá la configuración local: "+(e instanceof IllegalArgumentException?e.getMessage():"no se pudo leer el archivo.") : ErrorAplicacion.mensaje(e);
            System.err.println(mensaje);
            if(!GraphicsEnvironment.isHeadless()) JOptionPane.showMessageDialog(null,mensaje,"Materiales",JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }
}
