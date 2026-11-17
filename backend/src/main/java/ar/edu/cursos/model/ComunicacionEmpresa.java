package ar.edu.cursos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "comunicacion_empresa")
public class ComunicacionEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "comunicacion_id", nullable = false, unique = true, length = 100)
    private String comunicacionId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "curso_id", nullable = false)
    private Curso curso;

    @Column(nullable = false, length = 30)
    private String tipo;

    @Column(name = "hash_payload", nullable = false)
    private String hashPayload;

    @Column(name = "creada_en", nullable = false)
    private OffsetDateTime creadaEn;

    @PrePersist
    void alCrear() {
        creadaEn = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getComunicacionId() { return comunicacionId; }
    public void setComunicacionId(String comunicacionId) { this.comunicacionId = comunicacionId; }
    public Curso getCurso() { return curso; }
    public void setCurso(Curso curso) { this.curso = curso; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getHashPayload() { return hashPayload; }
    public void setHashPayload(String hashPayload) { this.hashPayload = hashPayload; }
    public OffsetDateTime getCreadaEn() { return creadaEn; }
}