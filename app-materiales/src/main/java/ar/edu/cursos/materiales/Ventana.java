package ar.edu.cursos.materiales;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** El EDT solo dibuja; todas las consultas y comandos se ejecutan en SwingWorker. */
public final class Ventana extends JFrame {
    private final Configuracion config;
    private Motor motor;
    private MaterialesService servicio;
    private Usuario usuario;
    private boolean ocupada;
    private final CardLayout tarjetas=new CardLayout();
    private final JPanel contenido=new JPanel(tarjetas);
    private final JTextArea estado=new JTextArea("Ingresá con tu usuario de Administración.",2,40);
    private final JTextField identificador=new JTextField(24);
    private final JPasswordField clave=new JPasswordField(24);
    private final JButton ingresar=new JButton("Ingresar");
    private final JLabel identidad=new JLabel();
    private final JComboBox<String> bandeja=new JComboBox<>(new String[]{"Asignadas", "Disponibles para reclamar"});
    private final JButton actualizar=new JButton("Actualizar");
    private final JButton analisis=new JButton("Análisis histórico (4.3)");
    private final JButton detalle=new JButton("Ver detalle");
    private final JButton reclamar=new JButton("Reclamar");
    private final JButton completar=new JButton("Completar materiales");
    private final JButton salir=new JButton("Cerrar sesión");
    private final JButton anterior=new JButton("Anterior"), siguiente=new JButton("Siguiente");
    private final JLabel paginado=new JLabel();
    private final DefaultTableModel modelo=new DefaultTableModel(new String[]{"Curso / temática", "Empresa", "Creada", "Asignado", "ID tarea"},0) {
        @Override public boolean isCellEditable(int r,int c) { return false; }
    };
    private final JTable tabla=new JTable(modelo);
    private List<Material> tareas=List.of();
    private int pagina;
    private long total;
    private static final DateTimeFormatter FECHA=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/Argentina/Cordoba"));
    private record Pagina(List<Material> items,long total) {}

    public Ventana(Configuracion config) {
        super("Preparación de materiales · Cursos"); this.config=config;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(850,500)); setSize(1050,620); setLocationRelativeTo(null);
        contenido.add(login(),"login"); contenido.add(bandeja(),"bandeja");
        estado.setBorder(BorderFactory.createEmptyBorder(10,14,10,14));
        estado.setEditable(false);estado.setLineWrap(true);estado.setWrapStyleWord(true);estado.setOpaque(false);estado.setFocusable(false);
        add(contenido,BorderLayout.CENTER);add(estado,BorderLayout.SOUTH);
        ingresar.addActionListener(e->loginUsuario()); clave.addActionListener(e->loginUsuario());
        actualizar.addActionListener(e->refrescar());
        analisis.addActionListener(e->verAnalisisHistorico());
        bandeja.addActionListener(e->{pagina=0;refrescar();});
        anterior.addActionListener(e->{pagina--;refrescar();});
        siguiente.addActionListener(e->{pagina++;refrescar();});
        tabla.getSelectionModel().addListSelectionListener(e->botones());
        detalle.addActionListener(e->verDetalle());
        reclamar.addActionListener(e->operar(false)); completar.addActionListener(e->operar(true));
        salir.addActionListener(e->{ if(ocupada)return;servicio.salir();usuario=null;tareas=List.of();modelo.setRowCount(0);identificador.setText("");clave.setText("");tarjetas.show(contenido,"login");getRootPane().setDefaultButton(ingresar);estado.setText("Sesión cerrada.");identificador.requestFocusInWindow(); });
        addWindowListener(new WindowAdapter(){@Override public void windowClosing(WindowEvent e){cerrar();}});
        getRootPane().setDefaultButton(ingresar);botones();
    }
    private JPanel login() {
        JPanel centro=new JPanel(new GridBagLayout()), form=new JPanel(new GridLayout(0,1,8,8));
        form.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("Acceso de Administración"),BorderFactory.createEmptyBorder(18,24,18,24)));
        JLabel userLabel=new JLabel("Usuario");userLabel.setLabelFor(identificador);
        JLabel passLabel=new JLabel("Contraseña");passLabel.setLabelFor(clave);
        form.add(new JLabel("Preparar materiales didácticos"));form.add(userLabel);form.add(identificador);form.add(passLabel);form.add(clave);form.add(ingresar);
        centro.add(form);return centro;
    }
    private JPanel bandeja() {
        JPanel p=new JPanel(new BorderLayout(8,12));p.setBorder(BorderFactory.createEmptyBorder(18,18,10,18));
        JPanel arriba=new JPanel(new BorderLayout());arriba.add(identidad,BorderLayout.WEST);arriba.add(salir,BorderLayout.EAST);
        JPanel filtro=new JPanel(new FlowLayout(FlowLayout.LEFT));filtro.add(new JLabel("Bandeja:"));filtro.add(bandeja);filtro.add(actualizar);filtro.add(analisis);
        JPanel encabezado=new JPanel(new BorderLayout());encabezado.add(arriba,BorderLayout.NORTH);encabezado.add(filtro,BorderLayout.SOUTH);
        p.add(encabezado,BorderLayout.NORTH);tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);tabla.setRowHeight(28);
        tabla.setAutoCreateRowSorter(true);tabla.setFillsViewportHeight(true);
        p.add(new JScrollPane(tabla),BorderLayout.CENTER);
        JPanel acciones=new JPanel(new FlowLayout(FlowLayout.LEFT));acciones.add(detalle);acciones.add(reclamar);acciones.add(completar);
        JPanel paginas=new JPanel(new FlowLayout(FlowLayout.RIGHT));paginas.add(anterior);paginas.add(paginado);paginas.add(siguiente);
        JPanel pie=new JPanel(new BorderLayout());pie.add(acciones,BorderLayout.NORTH);pie.add(paginas,BorderLayout.SOUTH);p.add(pie,BorderLayout.SOUTH);return p;
    }
    private void loginUsuario() {
        if(ocupada)return;
        String id=identificador.getText().strip();char[] password=clave.getPassword();clave.setText("");
        if(id.isBlank()||password.length==0){Arrays.fill(password,'\0');estado.setText("Completá usuario y contraseña.");return;}
        ejecutar(()->{
            try {
                if(motor==null) {motor=new Motor(config);servicio=new MaterialesService(motor.engine(),config);}
                return servicio.ingresar(id,password);
            } finally {Arrays.fill(password,'\0');}
        },u->{usuario=u;identidad.setText("Administración · "+u);pagina=0;tarjetas.show(contenido,"bandeja");getRootPane().setDefaultButton(null);refrescar();},"Conectando y validando usuario…");
    }
    private boolean disponibles(){return bandeja.getSelectedIndex()==1;}
    private Pagina cargar(boolean disponibles,int numero) {
        return new Pagina(servicio.listar(disponibles,numero),servicio.cantidad(disponibles));
    }
    private void refrescar(){
        if(ocupada||usuario==null)return;
        boolean disp=disponibles();int n=pagina;
        ejecutar(()->cargar(disp,n),this::mostrar,"Consultando materiales…");
    }
    private void mostrar(Pagina datos){
        tareas=datos.items();total=datos.total();modelo.setRowCount(0);
        for(Material m:tareas)modelo.addRow(new Object[]{m.tematica().isBlank()?"Curso sin temática disponible":m.tematica(),m.empresa(),m.creada()==null?"":FECHA.format(m.creada()),m.asignado()==null?"Sin asignar":m.asignado(),m.id()});
        paginado.setText("Página "+(pagina+1)+" · "+total+" tareas");
        estado.setText(tareas.isEmpty()?"No hay tareas en esta página. Podés actualizar o cambiar de bandeja.":"Seleccioná una tarea para ver su detalle o realizar una acción.");botones();
    }
    private Material seleccionada(){
        int fila=tabla.getSelectedRow();
        if(fila<0 || fila>=tabla.getRowCount())return null;
        int indice=tabla.convertRowIndexToModel(fila);
        return indice<tareas.size()?tareas.get(indice):null;
    }
    private void verDetalle(){
        Material m=seleccionada();if(m==null||ocupada)return;
        ejecutar(()->servicio.detalle(m.id()),d->{
            JTextArea texto=new JTextArea("Curso: "+d.tematica()+"\nEmpresa: "+d.empresa()+"\n\nDescripción:\n"+d.descripcion()+"\n\nCurso ID: "+d.cursoId()+"\nInstancia: "+d.instancia()+"\nTarea: "+d.id()+"\nAsignado: "+(d.asignado()==null?"Sin asignar":d.asignado()),15,65);
            texto.setEditable(false);texto.setLineWrap(true);texto.setWrapStyleWord(true);texto.setCaretPosition(0);
            JOptionPane.showMessageDialog(this,new JScrollPane(texto),"Detalle de materiales",JOptionPane.INFORMATION_MESSAGE);
        },"Consultando detalle…");
    }
    private void operar(boolean esCompletar){
        Material m=seleccionada();if(m==null||ocupada)return;
        String accion=esCompletar?"completar":"reclamar";
        if(JOptionPane.showConfirmDialog(this,"¿Querés "+accion+" los materiales del curso «"+m.tematica()+"»?"+(esCompletar?"\nConfirmá solo cuando estén preparados. Esta acción hace avanzar el proceso.":""),"Confirmar acción",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        boolean disp=disponibles();int n=pagina;
        ejecutar(()->{if(esCompletar)servicio.completar(m.id());else servicio.reclamar(m.id());return cargar(disp,n);},datos->{mostrar(datos);estado.setText(esCompletar?"Materiales completados. Bandeja actualizada.":"Tarea reclamada. La encontrás en Asignadas.");},esCompletar?"Completando materiales…":"Reclamando tarea…");
    }
    private void verAnalisisHistorico(){
        if(ocupada||motor==null)return;
        ejecutar(()->new AnalisisHistoricoService(motor.engine(),config).generarReporte(),this::mostrarDialogoAnalisis,"Consultando historial de ejecuciones en Flowable…");
    }
    private void mostrarDialogoAnalisis(AnalisisHistoricoService.ReporteHistorico r){
        JDialog dialogo=new JDialog(this,"Análisis de las ejecuciones del proceso (§4.3)",true);
        dialogo.setSize(860,640);dialogo.setLocationRelativeTo(this);
        JPanel contenedor=new JPanel(new GridLayout(3,1,10,10));
        contenedor.setBorder(BorderFactory.createEmptyBorder(14,14,10,14));

        // 4.3.1 Resultados de las solicitudes
        DefaultTableModel mResultados=new DefaultTableModel(new String[]{"Resultado de la solicitud", "Cantidad", "Porcentaje (%)"},0){
            @Override public boolean isCellEditable(int row,int col){return false;}
        };
        for(var f:r.resultadosSolicitudes()){
            mResultados.addRow(new Object[]{f.resultado(),f.cantidad(),String.format(java.util.Locale.ROOT,"%.2f%%",f.porcentaje())});
        }
        JTable tResultados=new JTable(mResultados);tResultados.setRowHeight(24);tResultados.setFillsViewportHeight(true);
        JPanel p1=new JPanel(new BorderLayout(4,4));
        p1.setBorder(BorderFactory.createTitledBorder("4.3.1 Resultados de las solicitudes (Total instancias terminadas: "+r.totalInstanciasTerminadas()+")"));
        p1.add(new JScrollPane(tResultados),BorderLayout.CENTER);
        contenedor.add(p1);

        // 4.3.2 Análisis de la preparación paralela
        DefaultTableModel mRamas=new DefaultTableModel(new String[]{"Rama de preparación", "Cursos completados", "Duración promedio"},0){
            @Override public boolean isCellEditable(int row,int col){return false;}
        };
        for(var f:r.ramasPreparacion()){
            mRamas.addRow(new Object[]{f.rama(),f.cursosConsiderados(),f.duracionPromedioFormateada()});
        }
        JTable tRamas=new JTable(mRamas);tRamas.setRowHeight(24);tRamas.setFillsViewportHeight(true);
        JPanel p2=new JPanel(new BorderLayout(4,4));
        p2.setBorder(BorderFactory.createTitledBorder("4.3.2 Análisis de la preparación paralela (Cursos con preparación completa: "+r.totalCursosPreparacionCompleta()+")"));
        p2.add(new JScrollPane(tRamas),BorderLayout.CENTER);
        contenedor.add(p2);

        // 4.3.3 Distribución de tareas
        DefaultTableModel mTareas=new DefaultTableModel(new String[]{"Usuario", "Nombre", "Rol / Grupo", "Tareas completadas"},0){
            @Override public boolean isCellEditable(int row,int col){return false;}
        };
        for(var f:r.distribucionTareas()){
            mTareas.addRow(new Object[]{f.usuarioId(),f.nombre(),f.rol(),f.tareasCompletadas()});
        }
        JTable tTareas=new JTable(mTareas);tTareas.setRowHeight(24);tTareas.setFillsViewportHeight(true);
        JPanel p3=new JPanel(new BorderLayout(4,4));
        p3.setBorder(BorderFactory.createTitledBorder("4.3.3 Distribución de tareas de usuario completadas por administrativo y docente"));
        p3.add(new JScrollPane(tTareas),BorderLayout.CENTER);
        contenedor.add(p3);

        JButton btnCerrar=new JButton("Cerrar");
        btnCerrar.addActionListener(e->dialogo.dispose());
        JPanel pie=new JPanel(new FlowLayout(FlowLayout.RIGHT));
        pie.setBorder(BorderFactory.createEmptyBorder(0,14,10,14));
        pie.add(btnCerrar);

        dialogo.add(contenedor,BorderLayout.CENTER);
        dialogo.add(pie,BorderLayout.SOUTH);
        estado.setText("Análisis histórico cargado ("+r.totalInstanciasTerminadas()+" instancias terminadas).");
        dialogo.setVisible(true);
    }
    private void botones(){
        Material m=seleccionada();boolean listo=!ocupada&&usuario!=null;
        ingresar.setEnabled(!ocupada);identificador.setEnabled(!ocupada);clave.setEnabled(!ocupada);
        actualizar.setEnabled(listo);analisis.setEnabled(listo);bandeja.setEnabled(listo);salir.setEnabled(listo);tabla.setEnabled(!ocupada);
        detalle.setEnabled(listo&&m!=null);reclamar.setEnabled(listo&&m!=null&&m.asignado()==null);
        completar.setEnabled(listo&&m!=null&&usuario.id().equals(m.asignado()));
        anterior.setEnabled(listo&&pagina>0);siguiente.setEnabled(listo&&(long)(pagina+1)*20<total);
    }
    private <T> void ejecutar(Callable<T> trabajo,Consumer<T> terminado,String mensaje){
        if(ocupada)return;ocupada=true;botones();estado.setText(mensaje);setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<T,Void>(){
            @Override protected T doInBackground() throws Exception{return trabajo.call();}
            @Override protected void done(){
                ocupada=false;setCursor(Cursor.getDefaultCursor());
                try{T valor=get();estado.setText("Operación realizada.");terminado.accept(valor);}
                catch(Exception e){String error=ErrorAplicacion.mensaje(e);estado.setText(error);JOptionPane.showMessageDialog(Ventana.this,error,"No se pudo realizar la operación",JOptionPane.WARNING_MESSAGE);}
                finally{botones();}
            }
        }.execute();
    }
    private void cerrar(){
        if(ocupada){estado.setText("Esperá a que termine la operación para cerrar.");return;}
        ejecutar(()->{if(servicio!=null)servicio.salir();if(motor!=null)motor.close();return true;},v->{dispose();System.exit(0);},"Cerrando conexión…");
    }
}
