package ar.edu.cursos.simulador;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public final class Ventana extends JFrame {

    private final SimuladorClient cliente;
    private boolean ocupada;

    private final JTextArea estado = new JTextArea("Seleccioná una solicitud para revisar su propuesta PDF o simular las comunicaciones de la empresa.", 2, 40);
    private final JButton actualizar = new JButton("Actualizar solicitudes");
    private final JButton detalle = new JButton("Ver propuesta recibida");
    private final JButton verPdf = new JButton("Abrir PDF de propuesta");
    private final JButton aceptar = new JButton("Aceptar propuesta");
    private final JButton modificar = new JButton("Solicitar modificaciones");
    private final JButton rechazar = new JButton("Rechazar propuesta");
    private final JButton nomina = new JButton("Enviar nómina");
    private final JButton cancelar = new JButton("Cancelar curso");

    private final DefaultTableModel modelo = new DefaultTableModel(
            new String[]{"Empresa solicitante", "Curso / temática", "Estado", "Propuesta recibida", "Presupuesto", "Comunicaciones habilitadas"}, 0
    ) {
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };
    private final JTable tabla = new JTable(modelo);
    private List<SolicitudSimulacion> solicitudes = List.of();

    public Ventana(Configuracion config) {
        super("Simulador REST · Comunicaciones de la Empresa");
        this.cliente = new SimuladorClient(config);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(960, 520));
        setSize(1120, 620);
        setLocationRelativeTo(null);

        add(panelPrincipal(), BorderLayout.CENTER);

        estado.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        estado.setEditable(false);
        estado.setLineWrap(true);
        estado.setWrapStyleWord(true);
        estado.setOpaque(false);
        estado.setFocusable(false);
        add(estado, BorderLayout.SOUTH);

        actualizar.addActionListener(e -> refrescar());
        tabla.getSelectionModel().addListSelectionListener(e -> botones());
        detalle.addActionListener(e -> verDetallePropuesta());
        verPdf.addActionListener(e -> abrirPdfPropuesta());
        aceptar.addActionListener(e -> simularAceptacion());
        modificar.addActionListener(e -> simularModificacion());
        rechazar.addActionListener(e -> simularRechazo());
        nomina.addActionListener(e -> simularNomina());
        cancelar.addActionListener(e -> simularCancelacion());

        botones();
        SwingUtilities.invokeLater(this::refrescar);
    }

    private JPanel panelPrincipal() {
        JPanel p = new JPanel(new BorderLayout(8, 12));
        p.setBorder(BorderFactory.createEmptyBorder(18, 18, 10, 18));

        JPanel arriba = new JPanel(new BorderLayout());
        arriba.add(new JLabel("Bandeja de simulación de la Empresa (negociación de propuesta, envío de nómina y cancelación)"), BorderLayout.WEST);
        arriba.add(actualizar, BorderLayout.EAST);
        p.add(arriba, BorderLayout.NORTH);

        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabla.setRowHeight(28);
        tabla.setAutoCreateRowSorter(true);
        tabla.setFillsViewportHeight(true);
        p.add(new JScrollPane(tabla), BorderLayout.CENTER);

        JPanel filaPropuesta = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        filaPropuesta.add(detalle);
        filaPropuesta.add(verPdf);
        filaPropuesta.add(aceptar);
        filaPropuesta.add(modificar);
        filaPropuesta.add(rechazar);

        JPanel filaPreparacion = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        filaPreparacion.add(nomina);
        filaPreparacion.add(cancelar);

        JPanel acciones = new JPanel(new GridLayout(2, 1, 0, 2));
        acciones.add(filaPropuesta);
        acciones.add(filaPreparacion);
        p.add(acciones, BorderLayout.SOUTH);
        return p;
    }

    private void refrescar() {
        if (ocupada) return;
        ejecutar(cliente::listarSolicitudes, this::mostrar, "Consultando solicitudes y comunicaciones habilitadas…");
    }

    private void mostrar(List<SolicitudSimulacion> datos) {
        solicitudes = datos;
        modelo.setRowCount(0);
        for (SolicitudSimulacion s : solicitudes) {
            String ver = s.propuestaVersionVigente() == null ? "Sin propuesta" : "v" + s.propuestaVersionVigente();
            String pres = (s.presupuestoPropuesto() == null || s.presupuestoPropuesto().isBlank()) ? "-" : s.presupuestoPropuesto();
            String acc = s.accionesHabilitadas().isEmpty() ? "Ninguna" : String.join(", ", s.accionesHabilitadas());
            modelo.addRow(new Object[]{
                    s.empresaNombre(),
                    s.tematica(),
                    s.estado(),
                    ver,
                    pres,
                    acc
            });
        }
        estado.setText(solicitudes.isEmpty()
                ? "No hay solicitudes registradas. Creá una solicitud desde la web pública primero."
                : "Seleccioná la solicitud de la empresa para leer la propuesta PDF o enviar una respuesta.");
        botones();
    }

    private SolicitudSimulacion seleccionada() {
        int fila = tabla.getSelectedRow();
        if (fila < 0 || fila >= tabla.getRowCount()) return null;
        int indice = tabla.convertRowIndexToModel(fila);
        return indice < solicitudes.size() ? solicitudes.get(indice) : null;
    }

    private void verDetallePropuesta() {
        SolicitudSimulacion s = seleccionada();
        if (s == null || ocupada) return;
        ejecutar(() -> cliente.consultarComunicaciones(s.cursoId()), d -> {
            String fechas = d.fechasPosibles().isEmpty() ? "Sin fechas cargadas" : String.join("\n  - ", d.fechasPosibles());
            String acciones = d.accionesHabilitadas().isEmpty() ? "Ninguna" : String.join(", ", d.accionesHabilitadas());
            String info = "Empresa: " + d.empresaNombre()
                    + "\nCurso / Temática: " + d.tematica()
                    + "\nCurso ID: " + d.cursoId()
                    + "\nEstado actual: " + d.estado()
                    + "\nVersión de propuesta vigente: " + (d.propuestaVersionVigente() == null ? "Sin propuesta enviada" : "v" + d.propuestaVersionVigente())
                    + "\nPresupuesto cotizado: " + (d.presupuestoPropuesto() == null || d.presupuestoPropuesto().isBlank() ? "-" : d.presupuestoPropuesto())
                    + "\n\nPrograma propuesto:\n" + (d.programaPropuesto() == null || d.programaPropuesto().isBlank() ? "-" : d.programaPropuesto())
                    + "\n\nFechas posibles ofrecidas:\n  - " + fechas
                    + "\n\nComunicaciones habilitadas para la empresa:\n  " + acciones;
            JTextArea texto = new JTextArea(info, 16, 62);
            texto.setEditable(false);
            texto.setLineWrap(true);
            texto.setWrapStyleWord(true);
            texto.setCaretPosition(0);
            JOptionPane.showMessageDialog(this, new JScrollPane(texto), "Propuesta recibida por la empresa", JOptionPane.INFORMATION_MESSAGE);
        }, "Consultando propuesta recibida…");
    }

    private void abrirPdfPropuesta() {
        SolicitudSimulacion s = seleccionada();
        if (s == null || ocupada || !s.tienePropuesta()) return;
        int version = s.propuestaVersionVigente();
        ejecutar(() -> cliente.descargarPdfPropuesta(s.cursoId(), version), rutaPdf -> {
            estado.setText("PDF descargado en: " + rutaPdf);
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(rutaPdf.toFile());
                } else {
                    JOptionPane.showMessageDialog(this, "PDF guardado en:\n" + rutaPdf, "PDF de propuesta", JOptionPane.INFORMATION_MESSAGE);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "PDF descargado en:\n" + rutaPdf, "PDF de propuesta", JOptionPane.INFORMATION_MESSAGE);
            }
        }, "Descargando PDF de la propuesta v" + version + "…");
    }

    private void simularAceptacion() {
        SolicitudSimulacion s = seleccionada();
        if (s == null || ocupada || !s.admite("ACEPTACION")) return;
        int version = s.propuestaVersionVigente() != null ? s.propuestaVersionVigente() : 1;

        JComboBox<String> comboFecha = new JComboBox<>(s.fechasPosibles().toArray(new String[0]));
        comboFecha.setEditable(true);
        if (comboFecha.getItemCount() == 0) {
            comboFecha.addItem("2026-12-01T09:00:00-03:00");
        }

        JPanel panel = new JPanel(new GridLayout(0, 1, 6, 6));
        panel.add(new JLabel("Empresa: " + s.empresaNombre() + " · Propuesta v" + version + " (" + s.presupuestoPropuesto() + ")"));
        panel.add(new JLabel("Seleccioná o ingresá la fecha acordada de inicio (ISO-8601):"));
        panel.add(comboFecha);

        if (JOptionPane.showConfirmDialog(this, panel, "Aceptar Propuesta", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
            return;
        }
        Object item = comboFecha.getSelectedItem();
        String fecha = item != null ? item.toString().strip() : "";
        if (fecha.isBlank()) {
            JOptionPane.showMessageDialog(this, "Debés indicar la fecha acordada.", "Validación", JOptionPane.WARNING_MESSAGE);
            return;
        }
        enviarYRefrescar(s.cursoId(), "ACEPTACION", version, fecha, null, null);
    }

    private void simularRechazo() {
        SolicitudSimulacion s = seleccionada();
        if (s == null || ocupada || !s.admite("RECHAZO")) return;
        int version = s.propuestaVersionVigente() != null ? s.propuestaVersionVigente() : 1;
        if (JOptionPane.showConfirmDialog(
                this,
                "¿Confirmás que la empresa «" + s.empresaNombre() + "» RECHAZA la propuesta v" + version + "?",
                "Rechazar Propuesta",
                JOptionPane.OK_CANCEL_OPTION
        ) != JOptionPane.OK_OPTION) {
            return;
        }
        enviarYRefrescar(s.cursoId(), "RECHAZO", version, null, null, null);
    }

    private void simularModificacion() {
        SolicitudSimulacion s = seleccionada();
        if (s == null || ocupada || !s.admite("MODIFICACION")) return;
        int version = s.propuestaVersionVigente() != null ? s.propuestaVersionVigente() : 1;

        JTextArea txtObs = new JTextArea("Solicitamos revisar el presupuesto y agregar fechas en la segunda quincena.", 5, 42);
        txtObs.setLineWrap(true);
        txtObs.setWrapStyleWord(true);

        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.add(new JLabel("Observaciones de «" + s.empresaNombre() + "» sobre la propuesta v" + version + ":"), BorderLayout.NORTH);
        panel.add(new JScrollPane(txtObs), BorderLayout.CENTER);

        if (JOptionPane.showConfirmDialog(this, panel, "Solicitar Modificaciones", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
            return;
        }
        String obs = txtObs.getText().strip();
        if (obs.isBlank()) {
            JOptionPane.showMessageDialog(this, "Las observaciones no pueden estar vacías.", "Validación", JOptionPane.WARNING_MESSAGE);
            return;
        }
        enviarYRefrescar(s.cursoId(), "MODIFICACION", version, null, obs, null);
    }

    private void simularNomina() {
        SolicitudSimulacion s = seleccionada();
        if (s == null || ocupada || !s.admite("NOMINA")) return;

        JTextArea txtPart = new JTextArea("Ana Pérez\nJuan Gómez\nLucía Fernández", 6, 40);
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.add(new JLabel("Nómina de participantes enviada por «" + s.empresaNombre() + "» (uno por línea):"), BorderLayout.NORTH);
        panel.add(new JScrollPane(txtPart), BorderLayout.CENTER);

        if (JOptionPane.showConfirmDialog(this, panel, "Enviar Nómina de Participantes", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
            return;
        }
        List<String> participantes = new ArrayList<>();
        for (String linea : txtPart.getText().split("\\R")) {
            if (!linea.isBlank()) {
                participantes.add(linea.strip());
            }
        }
        if (participantes.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Debés ingresar al menos un participante.", "Validación", JOptionPane.WARNING_MESSAGE);
            return;
        }
        enviarYRefrescar(s.cursoId(), "NOMINA", null, null, null, participantes);
    }

    private void simularCancelacion() {
        SolicitudSimulacion s = seleccionada();
        if (s == null || ocupada || !s.admite("CANCELACION")) return;
        if (JOptionPane.showConfirmDialog(
                this,
                "¿Confirmás que «" + s.empresaNombre() + "» solicita la CANCELACIÓN del curso «" + s.tematica() + "» durante la preparación?\nEsto interrumpirá la preparación y lanzará las compensaciones.",
                "Cancelar Curso",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE
        ) != JOptionPane.OK_OPTION) {
            return;
        }
        enviarYRefrescar(s.cursoId(), "CANCELACION", null, null, null, null);
    }

    private void enviarYRefrescar(
            String cursoId,
            String tipo,
            Integer version,
            String fechaCurso,
            String observaciones,
            List<String> participantes
    ) {
        ejecutar(() -> {
            String msg = cliente.enviarComunicacion(cursoId, tipo, version, fechaCurso, observaciones, participantes);
            List<SolicitudSimulacion> actualizadas = cliente.listarSolicitudes();
            return new ResultadoAccion(msg, actualizadas);
        }, res -> {
            mostrar(res.solicitudes());
            estado.setText(res.mensaje());
            JOptionPane.showMessageDialog(this, res.mensaje(), "Comunicación enviada", JOptionPane.INFORMATION_MESSAGE);
        }, "Enviando comunicación " + tipo + "…");
    }

    private record ResultadoAccion(String mensaje, List<SolicitudSimulacion> solicitudes) {}

    private void botones() {
        SolicitudSimulacion s = seleccionada();
        boolean listo = !ocupada;
        actualizar.setEnabled(listo);
        tabla.setEnabled(listo);
        detalle.setEnabled(listo && s != null);
        verPdf.setEnabled(listo && s != null && s.tienePropuesta());
        aceptar.setEnabled(listo && s != null && s.admite("ACEPTACION"));
        rechazar.setEnabled(listo && s != null && s.admite("RECHAZO"));
        modificar.setEnabled(listo && s != null && s.admite("MODIFICACION"));
        nomina.setEnabled(listo && s != null && s.admite("NOMINA"));
        cancelar.setEnabled(listo && s != null && s.admite("CANCELACION"));
    }

    private <T> void ejecutar(Callable<T> trabajo, Consumer<T> terminado, String mensaje) {
        if (ocupada) return;
        ocupada = true;
        botones();
        estado.setText(mensaje);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return trabajo.call();
            }

            @Override
            protected void done() {
                ocupada = false;
                setCursor(Cursor.getDefaultCursor());
                try {
                    T valor = get();
                    terminado.accept(valor);
                } catch (Exception e) {
                    String error = ErrorAplicacion.mensaje(e);
                    estado.setText(error);
                    JOptionPane.showMessageDialog(Ventana.this, error, "No se pudo realizar la operación", JOptionPane.WARNING_MESSAGE);
                } finally {
                    botones();
                }
            }
        }.execute();
    }
}
