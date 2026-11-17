package ar.edu.cursos.simulador;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public final class Ventana extends JFrame {

    private final SimuladorClient cliente;
    private String usuarioActual;
    private boolean ocupada;

    private final CardLayout tarjetas = new CardLayout();
    private final JPanel contenido = new JPanel(tarjetas);
    private final JTextArea estado = new JTextArea("Ingresá con tu usuario de personal para simular comunicaciones de empresa.", 2, 40);

    private final JTextField identificador = new JTextField("Jefe-Administrativo", 24);
    private final JPasswordField clave = new JPasswordField("1234", 24);
    private final JButton ingresar = new JButton("Ingresar");

    private final JLabel identidad = new JLabel();
    private final JButton actualizar = new JButton("Actualizar");
    private final JButton detalle = new JButton("Ver detalle");
    private final JButton aceptar = new JButton("Aceptar propuesta");
    private final JButton rechazar = new JButton("Rechazar propuesta");
    private final JButton modificar = new JButton("Pedir modificaciones");
    private final JButton nomina = new JButton("Enviar nómina");
    private final JButton cancelar = new JButton("Cancelar curso");
    private final JButton salir = new JButton("Cerrar sesión");

    private final DefaultTableModel modelo = new DefaultTableModel(
            new String[]{"Curso / temática", "Empresa", "Estado", "Propuesta vigente", "Acciones habilitadas", "ID curso"}, 0
    ) {
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };
    private final JTable tabla = new JTable(modelo);
    private List<SolicitudSimulacion> solicitudes = List.of();

    public Ventana(Configuracion config) {
        super("Simulador de comunicaciones REST · Cursos");
        this.cliente = new SimuladorClient(config);

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(920, 520));
        setSize(1100, 620);
        setLocationRelativeTo(null);

        contenido.add(panelLogin(), "login");
        contenido.add(panelBandeja(), "bandeja");

        estado.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        estado.setEditable(false);
        estado.setLineWrap(true);
        estado.setWrapStyleWord(true);
        estado.setOpaque(false);
        estado.setFocusable(false);

        add(contenido, BorderLayout.CENTER);
        add(estado, BorderLayout.SOUTH);

        ingresar.addActionListener(e -> loginUsuario());
        clave.addActionListener(e -> loginUsuario());
        actualizar.addActionListener(e -> refrescar());
        tabla.getSelectionModel().addListSelectionListener(e -> botones());
        detalle.addActionListener(e -> verDetalle());

        aceptar.addActionListener(e -> simularAceptacion());
        rechazar.addActionListener(e -> simularRechazo());
        modificar.addActionListener(e -> simularModificacion());
        nomina.addActionListener(e -> simularNomina());
        cancelar.addActionListener(e -> simularCancelacion());

        salir.addActionListener(e -> {
            if (ocupada) return;
            cliente.salir();
            usuarioActual = null;
            solicitudes = List.of();
            modelo.setRowCount(0);
            tarjetas.show(contenido, "login");
            getRootPane().setDefaultButton(ingresar);
            estado.setText("Sesión cerrada.");
            identificador.requestFocusInWindow();
            botones();
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cerrar();
            }
        });

        getRootPane().setDefaultButton(ingresar);
        botones();
    }

    private JPanel panelLogin() {
        JPanel centro = new JPanel(new GridBagLayout());
        JPanel form = new JPanel(new GridLayout(0, 1, 8, 8));
        form.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Acceso al Simulador REST"),
                BorderFactory.createEmptyBorder(18, 24, 18, 24)
        ));
        JLabel userLabel = new JLabel("Usuario del personal");
        userLabel.setLabelFor(identificador);
        JLabel passLabel = new JLabel("Contraseña");
        passLabel.setLabelFor(clave);
        form.add(new JLabel("Simulación de comunicaciones de empresa"));
        form.add(userLabel);
        form.add(identificador);
        form.add(passLabel);
        form.add(clave);
        form.add(ingresar);
        centro.add(form);
        return centro;
    }

    private JPanel panelBandeja() {
        JPanel p = new JPanel(new BorderLayout(8, 12));
        p.setBorder(BorderFactory.createEmptyBorder(18, 18, 10, 18));

        JPanel arriba = new JPanel(new BorderLayout());
        arriba.add(identidad, BorderLayout.WEST);
        JPanel controlesArriba = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        controlesArriba.add(actualizar);
        controlesArriba.add(salir);
        arriba.add(controlesArriba, BorderLayout.EAST);
        p.add(arriba, BorderLayout.NORTH);

        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabla.setRowHeight(28);
        tabla.setAutoCreateRowSorter(true);
        tabla.setFillsViewportHeight(true);
        p.add(new JScrollPane(tabla), BorderLayout.CENTER);

        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        acciones.add(detalle);
        acciones.add(aceptar);
        acciones.add(modificar);
        acciones.add(rechazar);
        acciones.add(nomina);
        acciones.add(cancelar);
        p.add(acciones, BorderLayout.SOUTH);
        return p;
    }

    private void loginUsuario() {
        if (ocupada) return;
        String id = identificador.getText().strip();
        char[] password = clave.getPassword();
        if (id.isBlank() || password.length == 0) {
            Arrays.fill(password, '\0');
            estado.setText("Completá usuario y contraseña.");
            return;
        }
        ejecutar(() -> {
            try {
                return cliente.ingresar(id, password);
            } finally {
                Arrays.fill(password, '\0');
            }
        }, u -> {
            usuarioActual = u;
            identidad.setText("Simulador REST · " + u);
            tarjetas.show(contenido, "bandeja");
            getRootPane().setDefaultButton(null);
            refrescar();
        }, "Conectando con el backend y validando sesión…");
    }

    private void refrescar() {
        if (ocupada || usuarioActual == null) return;
        ejecutar(cliente::listarSolicitudes, this::mostrar, "Consultando solicitudes y acciones habilitadas…");
    }

    private void mostrar(List<SolicitudSimulacion> datos) {
        solicitudes = datos;
        modelo.setRowCount(0);
        for (SolicitudSimulacion s : solicitudes) {
            String ver = s.propuestaVersionVigente() == null ? "Sin propuesta" : "v" + s.propuestaVersionVigente();
            String acc = s.accionesHabilitadas().isEmpty() ? "Ninguna" : String.join(", ", s.accionesHabilitadas());
            modelo.addRow(new Object[]{
                    s.tematica(),
                    s.empresaNombre(),
                    s.estado(),
                    ver,
                    acc,
                    s.cursoId()
            });
        }
        estado.setText(solicitudes.isEmpty()
                ? "No hay solicitudes registradas en el backend."
                : "Seleccioná un curso para ver las comunicaciones habilitadas en su estado actual.");
        botones();
    }

    private SolicitudSimulacion seleccionada() {
        int fila = tabla.getSelectedRow();
        if (fila < 0 || fila >= tabla.getRowCount()) return null;
        int indice = tabla.convertRowIndexToModel(fila);
        return indice < solicitudes.size() ? solicitudes.get(indice) : null;
    }

    private void verDetalle() {
        SolicitudSimulacion s = seleccionada();
        if (s == null || ocupada) return;
        ejecutar(() -> cliente.consultarComunicaciones(s.cursoId()), d -> {
            String fechas = d.fechasPosibles().isEmpty() ? "Sin fechas cargadas" : String.join("\n  - ", d.fechasPosibles());
            String acciones = d.accionesHabilitadas().isEmpty() ? "Ninguna" : String.join(", ", d.accionesHabilitadas());
            String info = "Curso / Temática: " + d.tematica()
                    + "\nEmpresa: " + d.empresaNombre()
                    + "\nCurso ID: " + d.cursoId()
                    + "\nEstado actual: " + d.estado()
                    + "\nVersión de propuesta vigente: " + (d.propuestaVersionVigente() == null ? "-" : "v" + d.propuestaVersionVigente())
                    + "\n\nFechas posibles de la propuesta:\n  - " + fechas
                    + "\n\nComunicaciones habilitadas ahora:\n  " + acciones;
            JTextArea texto = new JTextArea(info, 14, 60);
            texto.setEditable(false);
            texto.setLineWrap(true);
            texto.setWrapStyleWord(true);
            JOptionPane.showMessageDialog(this, new JScrollPane(texto), "Detalle de solicitud", JOptionPane.INFORMATION_MESSAGE);
        }, "Consultando estado del curso…");
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
        panel.add(new JLabel("Curso: " + s.tematica() + " (Propuesta v" + version + ")"));
        panel.add(new JLabel("Seleccioná o ingresá la fecha acordada (ISO-8601):"));
        panel.add(comboFecha);

        if (JOptionPane.showConfirmDialog(this, panel, "Simular Aceptación de Propuesta", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
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
                "¿Confirmás simular el RECHAZO de la propuesta v" + version + " para «" + s.tematica() + "»?",
                "Simular Rechazo de Propuesta",
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
        panel.add(new JLabel("Observaciones de la empresa sobre la propuesta v" + version + ":"), BorderLayout.NORTH);
        panel.add(new JScrollPane(txtObs), BorderLayout.CENTER);

        if (JOptionPane.showConfirmDialog(this, panel, "Simular Pedido de Modificaciones", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
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
        panel.add(new JLabel("Ingresá un participante por línea:"), BorderLayout.NORTH);
        panel.add(new JScrollPane(txtPart), BorderLayout.CENTER);

        if (JOptionPane.showConfirmDialog(this, panel, "Simular Envío de Nómina", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
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
                "¿Confirmás simular la CANCELACIÓN del curso «" + s.tematica() + "» durante la preparación?\nEsto interrumpirá la preparación y lanzará las compensaciones.",
                "Simular Cancelación",
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
        boolean listo = !ocupada && usuarioActual != null;
        ingresar.setEnabled(!ocupada);
        identificador.setEnabled(!ocupada);
        clave.setEnabled(!ocupada);
        actualizar.setEnabled(listo);
        salir.setEnabled(listo);
        tabla.setEnabled(!ocupada);
        detalle.setEnabled(listo && s != null);
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

    private void cerrar() {
        if (ocupada) {
            estado.setText("Esperá a que termine la operación para cerrar.");
            return;
        }
        cliente.salir();
        dispose();
        System.exit(0);
    }
}
