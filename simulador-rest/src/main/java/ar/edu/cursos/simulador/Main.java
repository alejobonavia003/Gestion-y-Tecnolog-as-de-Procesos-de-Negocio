package ar.edu.cursos.simulador;

import javax.swing.*;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;

public final class Main {

    public static void main(String[] args) {
        try {
            Path archivo = null;
            for (int i = 0; i < args.length; i++) {
                if ("--config".equals(args[i]) && i + 1 < args.length) {
                    archivo = Path.of(args[++i]);
                } else {
                    throw new IllegalArgumentException("Uso: java -jar simulador-rest.jar [--config archivo]");
                }
            }
            Configuracion config = Configuracion.cargar(archivo);
            if (GraphicsEnvironment.isHeadless()) {
                throw new ErrorAplicacion("No hay entorno gráfico disponible para abrir la ventana del simulador.");
            }
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            SwingUtilities.invokeLater(() -> new Ventana(config).setVisible(true));
        } catch (Exception e) {
            String mensaje = ErrorAplicacion.mensaje(e);
            System.err.println(mensaje);
            if (!GraphicsEnvironment.isHeadless()) {
                JOptionPane.showMessageDialog(null, mensaje, "Simulador REST", JOptionPane.ERROR_MESSAGE);
            }
            System.exit(1);
        }
    }
}
