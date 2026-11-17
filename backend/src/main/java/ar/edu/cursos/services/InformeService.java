package ar.edu.cursos.services;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import ar.edu.cursos.clients.FlowableClient;
import ar.edu.cursos.dto.SesionUsuario;
import ar.edu.cursos.exceptions.ApiException;
import ar.edu.cursos.model.Curso;
import ar.edu.cursos.repositories.CursoRepository;

/**
 * Etapa 9, paso 3: informe de resultados (#19).
 * El resumen lo carga el jefe docente en el formulario de elaborar_informe (variable
 * informeResumen). El backend lo copia a la base institucional la primera vez que lo
 * lee y genera el PDF al vuelo: no se guarda ningún archivo.
 */
@Service
public class InformeService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm 'hs'");
    private static final Color AZUL = new Color(24, 48, 89);

    private final CursoRepository cursos;
    private final FlowableClient motor;

    public InformeService(CursoRepository cursos, FlowableClient motor) {
        this.cursos = cursos;
        this.motor = motor;
    }

    // --- #19 GET /cursos/{cursoId}/informe/pdf -------------------------------

    @Transactional
    public byte[] pdf(UUID cursoId, SesionUsuario sesion) {
        Curso curso = cursos.findById(cursoId)
                .orElseThrow(() -> ApiException.noEncontrado("Curso no encontrado."));
        autorizar(curso, sesion);

        String resumen = resumen(curso)
                .orElseThrow(() -> ApiException.noEncontrado("El curso todavía no tiene informe."));
        String docente = curso.getProcessInstanceId() == null ? null
                : motor.variableHistorica(curso.getProcessInstanceId(), "docenteId").orElse(null);

        return generarPdf(curso, resumen, docente);
    }

    /**
     * El resumen guardado en la base o, si todavía no está, la variable informeResumen
     * del proceso (que se copia a la base para no volver a pedirla).
     */
    Optional<String> resumen(Curso curso) {
        if (curso.getInformeResumen() != null && !curso.getInformeResumen().isBlank()) {
            return Optional.of(curso.getInformeResumen());
        }
        if (curso.getProcessInstanceId() == null) return Optional.empty();

        Optional<String> variable = motor.variableHistorica(curso.getProcessInstanceId(), "informeResumen")
                .filter(r -> !r.isBlank());
        variable.ifPresent(r -> {
            curso.setInformeResumen(r);
            cursos.save(curso);
        });
        return variable;
    }

    /** Jefe administrativo y jefe docente: cualquier curso. Empresa: solo los suyos (si no, 404). */
    private static void autorizar(Curso curso, SesionUsuario sesion) {
        if (sesion.esEmpresa()) {
            Long duena = curso.getEmpresa() == null ? null : curso.getEmpresa().getId();
            if (duena == null || !duena.equals(sesion.empresaIdNumerico())) {
                throw ApiException.noEncontrado("Curso no encontrado.");
            }
            return;
        }
        if (!Boolean.TRUE.equals(sesion.esJefe())) {
            throw ApiException.sinPermiso("Solo los jefes administrativo y docente pueden ver el informe.");
        }
    }

    // --- PDF ----------------------------------------------------------------

    private static byte[] generarPdf(Curso curso, String resumen, String docente) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document documento = new Document(PageSize.A4, 48, 48, 54, 48);
            PdfWriter.getInstance(documento, out);
            documento.open();

            Font titulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, AZUL);
            Font subtitulo = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.DARK_GRAY);
            Font seccion = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, AZUL);
            Font texto = FontFactory.getFont(FontFactory.HELVETICA, 10.5f, Color.BLACK);
            Font negrita = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10.5f, Color.BLACK);

            Paragraph encabezado = new Paragraph("INFORME DE RESULTADOS DEL CURSO", titulo);
            encabezado.setSpacingAfter(4f);
            documento.add(encabezado);

            Paragraph meta = new Paragraph("Dirección de Vinculación | Emitido: "
                    + OffsetDateTime.now().format(FECHA), subtitulo);
            meta.setSpacingAfter(16f);
            documento.add(meta);

            PdfPTable datos = new PdfPTable(2);
            datos.setWidthPercentage(100);
            datos.setWidths(new float[] {32f, 68f});
            datos.setSpacingAfter(16f);
            fila(datos, "Curso / Temática:", valor(curso.getTematica()), negrita, texto);
            fila(datos, "Empresa:", curso.getEmpresa() == null ? "-" : valor(curso.getEmpresa().getNombre()), negrita, texto);
            fila(datos, "Docente:", valor(docente), negrita, texto);
            fila(datos, "Fecha del curso:", curso.getFechaCurso() == null ? "-" : curso.getFechaCurso().format(FECHA), negrita, texto);
            fila(datos, "Participantes estimados:", String.valueOf(curso.getCantidadParticipantes()), negrita, texto);
            documento.add(datos);

            Paragraph tituloResumen = new Paragraph("Resumen del dictado", seccion);
            tituloResumen.setSpacingAfter(6f);
            documento.add(tituloResumen);

            Paragraph cuerpo = new Paragraph(resumen, texto);
            cuerpo.setAlignment(Element.ALIGN_JUSTIFIED);
            documento.add(cuerpo);

            documento.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_GENERACION_PDF",
                    "No se pudo generar el PDF del informe.");
        }
    }

    private static String valor(String v) {
        return v == null || v.isBlank() ? "-" : v;
    }

    private static void fila(PdfPTable tabla, String etiqueta, String valor, Font fEtiqueta, Font fValor) {
        PdfPCell c1 = new PdfPCell(new Phrase(etiqueta, fEtiqueta));
        c1.setBackgroundColor(new Color(242, 245, 250));
        c1.setPadding(7f);
        c1.setBorderColor(new Color(210, 218, 230));
        PdfPCell c2 = new PdfPCell(new Phrase(valor, fValor));
        c2.setPadding(7f);
        c2.setBorderColor(new Color(210, 218, 230));
        tabla.addCell(c1);
        tabla.addCell(c2);
    }
}
