package ar.edu.cursos.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Cada versión enviada de la propuesta, con su PDF. No se pisan las anteriores. */
@Entity
@Table(name = "propuesta",
       uniqueConstraints = @UniqueConstraint(columnNames = {"curso_id", "version"}))
public class Propuesta {

    public enum Estado { ENVIADA, MODIFICACIONES, ACEPTADA, RECHAZADA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "curso_id", nullable = false)
    private Curso curso;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false, columnDefinition = "text")
    private String programa;

    @Column(name = "presupuesto_importe", nullable = false, precision = 14, scale = 2)
    private BigDecimal presupuestoImporte;

    @Column(name = "presupuesto_moneda", nullable = false, length = 3)
    private String presupuestoMoneda;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "propuesta_fecha", joinColumns = @JoinColumn(name = "propuesta_id"))
    @OrderColumn(name = "orden")
    @Column(name = "fecha", nullable = false)
    private List<OffsetDateTime> fechasPosibles = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado = Estado.ENVIADA;

    @Column(name = "observaciones_empresa", columnDefinition = "text")
    private String observacionesEmpresa;

    /** Ruta del PDF en disco (etapa 7). */
    @Column(name = "pdf_ruta")
    private String pdfRuta;

    @Column(name = "creada_en", nullable = false)
    private OffsetDateTime creadaEn;

    @PrePersist
    void alCrear() {
        creadaEn = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public Curso getCurso() { return curso; }
    public void setCurso(Curso curso) { this.curso = curso; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getPrograma() { return programa; }
    public void setPrograma(String programa) { this.programa = programa; }
    public BigDecimal getPresupuestoImporte() { return presupuestoImporte; }
    public void setPresupuestoImporte(BigDecimal presupuestoImporte) { this.presupuestoImporte = presupuestoImporte; }
    public String getPresupuestoMoneda() { return presupuestoMoneda; }
    public void setPresupuestoMoneda(String presupuestoMoneda) { this.presupuestoMoneda = presupuestoMoneda; }
    public List<OffsetDateTime> getFechasPosibles() { return fechasPosibles; }
    public void setFechasPosibles(List<OffsetDateTime> fechasPosibles) { this.fechasPosibles = fechasPosibles; }
    public Estado getEstado() { return estado; }
    public void setEstado(Estado estado) { this.estado = estado; }
    public String getObservacionesEmpresa() { return observacionesEmpresa; }
    public void setObservacionesEmpresa(String observacionesEmpresa) { this.observacionesEmpresa = observacionesEmpresa; }
    public String getPdfRuta() { return pdfRuta; }
    public void setPdfRuta(String pdfRuta) { this.pdfRuta = pdfRuta; }
    public OffsetDateTime getCreadaEn() { return creadaEn; }
}
