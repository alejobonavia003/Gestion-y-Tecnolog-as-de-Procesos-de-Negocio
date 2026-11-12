package ar.edu.cursos.model;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** Cuenta de acceso al portal. Se crea en "Habilitar acceso de la empresa". */
@Entity
@Table(name = "cuenta_empresa")
public class CuentaEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "empresa_id", nullable = false, unique = true)
    private Empresa empresa;

    @Column(nullable = false, unique = true)
    private String usuario;

    /** Solo el hash (BCrypt). Nunca la contraseña en texto plano. */
    @Column(name = "clave_hash", nullable = false)
    private String claveHash;

    @Column(nullable = false)
    private boolean activa = true;

    @Column(name = "habilitada_en", nullable = false)
    private OffsetDateTime habilitadaEn = OffsetDateTime.now();

    public Long getId() { return id; }
    public Empresa getEmpresa() { return empresa; }
    public void setEmpresa(Empresa empresa) { this.empresa = empresa; }
    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) { this.usuario = usuario; }
    public String getClaveHash() { return claveHash; }
    public void setClaveHash(String claveHash) { this.claveHash = claveHash; }
    public boolean isActiva() { return activa; }
    public void setActiva(boolean activa) { this.activa = activa; }
    public OffsetDateTime getHabilitadaEn() { return habilitadaEn; }
}
