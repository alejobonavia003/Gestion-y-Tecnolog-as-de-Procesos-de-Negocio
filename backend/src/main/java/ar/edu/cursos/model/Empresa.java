package ar.edu.cursos.model;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "empresa")
public class Empresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    /** CUIT u otro identificador. Opcional, pero único si se informa. */
    @Column(unique = true)
    private String identificador;

    @Column(name = "contacto_nombre", nullable = false)
    private String contactoNombre;

    @Column(name = "contacto_email", nullable = false)
    private String contactoEmail;

    @Column(name = "contacto_telefono")
    private String contactoTelefono;

    @Column(name = "creada_en", nullable = false)
    private OffsetDateTime creadaEn;

    @PrePersist
    void alCrear() {
        creadaEn = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getIdentificador() { return identificador; }
    public void setIdentificador(String identificador) { this.identificador = identificador; }
    public String getContactoNombre() { return contactoNombre; }
    public void setContactoNombre(String contactoNombre) { this.contactoNombre = contactoNombre; }
    public String getContactoEmail() { return contactoEmail; }
    public void setContactoEmail(String contactoEmail) { this.contactoEmail = contactoEmail; }
    public String getContactoTelefono() { return contactoTelefono; }
    public void setContactoTelefono(String contactoTelefono) { this.contactoTelefono = contactoTelefono; }
    public OffsetDateTime getCreadaEn() { return creadaEn; }
}
