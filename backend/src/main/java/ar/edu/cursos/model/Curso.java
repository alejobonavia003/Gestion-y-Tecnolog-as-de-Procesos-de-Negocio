package ar.edu.cursos.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "curso")
public class Curso {

    /** También es el businessKey ("curso:<id>") y la variable cursoId del proceso. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    // --- Datos de la solicitud ---
    @Column(nullable = false)
    private String tematica;

    @Column(nullable = false, columnDefinition = "text")
    private String descripcion;

    @Column(name = "cantidad_participantes", nullable = false)
    private int cantidadParticipantes;

    @Column(name = "fechas_preferidas", nullable = false)
    private String fechasPreferidas;

    // --- Datos acordados (se copian de la propuesta aceptada) ---
    @Column(columnDefinition = "text")
    private String programa;

    @Column(name = "presupuesto_importe", precision = 14, scale = 2)
    private BigDecimal presupuestoImporte;

    @Column(name = "presupuesto_moneda", length = 3)
    private String presupuestoMoneda;

    @Column(name = "fecha_curso")
    private OffsetDateTime fechaCurso;

    @Column(name = "informe_resumen", columnDefinition = "text")
    private String informeResumen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aula_id")
    private Aula aula;

    // --- Estado y vínculo con Flowable ---
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private EstadoCurso estado = EstadoCurso.SOLICITADO;

    @Column(name = "process_instance_id", unique = true)
    private String processInstanceId;

    // --- Idempotencia del formulario público ---
    @Column(name = "clave_idempotencia", unique = true)
    private String claveIdempotencia;

    /** Hash del cuerpo de la solicitud, para detectar un reintento con otros datos (409). */
    @Column(name = "hash_solicitud")
    private String hashSolicitud;

    @Column(name = "creado_en", nullable = false)
    private OffsetDateTime creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private OffsetDateTime actualizadoEn;

    @Version
    private long version;

    @PrePersist
    void alCrear() {
        creadoEn = OffsetDateTime.now();
        actualizadoEn = creadoEn;
    }

    @PreUpdate
    void alActualizar() {
        actualizadoEn = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public Empresa getEmpresa() { return empresa; }
    public void setEmpresa(Empresa empresa) { this.empresa = empresa; }
    public String getTematica() { return tematica; }
    public void setTematica(String tematica) { this.tematica = tematica; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public int getCantidadParticipantes() { return cantidadParticipantes; }
    public void setCantidadParticipantes(int cantidadParticipantes) { this.cantidadParticipantes = cantidadParticipantes; }
    public String getFechasPreferidas() { return fechasPreferidas; }
    public void setFechasPreferidas(String fechasPreferidas) { this.fechasPreferidas = fechasPreferidas; }
    public String getPrograma() { return programa; }
    public void setPrograma(String programa) { this.programa = programa; }
    public BigDecimal getPresupuestoImporte() { return presupuestoImporte; }
    public void setPresupuestoImporte(BigDecimal presupuestoImporte) { this.presupuestoImporte = presupuestoImporte; }
    public String getPresupuestoMoneda() { return presupuestoMoneda; }
    public void setPresupuestoMoneda(String presupuestoMoneda) { this.presupuestoMoneda = presupuestoMoneda; }
    public OffsetDateTime getFechaCurso() { return fechaCurso; }
    public void setFechaCurso(OffsetDateTime fechaCurso) { this.fechaCurso = fechaCurso; }
    public String getInformeResumen() { return informeResumen; }
    public void setInformeResumen(String informeResumen) { this.informeResumen = informeResumen; }
    public Aula getAula() { return aula; }
    public void setAula(Aula aula) { this.aula = aula; }
    public EstadoCurso getEstado() { return estado; }
    public void setEstado(EstadoCurso estado) { this.estado = estado; }
    public String getProcessInstanceId() { return processInstanceId; }
    public void setProcessInstanceId(String processInstanceId) { this.processInstanceId = processInstanceId; }
    public String getClaveIdempotencia() { return claveIdempotencia; }
    public void setClaveIdempotencia(String claveIdempotencia) { this.claveIdempotencia = claveIdempotencia; }
    public String getHashSolicitud() { return hashSolicitud; }
    public void setHashSolicitud(String hashSolicitud) { this.hashSolicitud = hashSolicitud; }
    public OffsetDateTime getCreadoEn() { return creadoEn; }
    public OffsetDateTime getActualizadoEn() { return actualizadoEn; }
}
