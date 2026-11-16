package ar.edu.cursos.services;

import ar.edu.cursos.clients.FlowableClient;
import ar.edu.cursos.dto.NuevaPropuestaRequest;
import ar.edu.cursos.dto.PropuestaCreadaResponse;
import ar.edu.cursos.dto.PropuestaItemResponse;
import ar.edu.cursos.dto.SesionUsuario;
import ar.edu.cursos.dto.TareaDetalle;
import ar.edu.cursos.dto.TareaResumen;
import ar.edu.cursos.exceptions.ApiException;
import ar.edu.cursos.model.Curso;
import ar.edu.cursos.model.Propuesta;
import ar.edu.cursos.repositories.CursoRepository;
import ar.edu.cursos.repositories.PropuestaRepository;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PropuestaService {

    private static final Set<String> TAREAS_PROPUESTA = Set.of(
            "registrar_elaborar_propuesta",
            "revisar_propuesta"
    );

    private static final DateTimeFormatter FECHA_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm 'hs' (XXX)");

    private final PropuestaRepository propuestaRepository;
    private final CursoRepository cursoRepository;
    private final FlowableClient flowableClient;
    private final TareaService tareaService;
    private final Path directorioPropuestas;

    public PropuestaService(
            PropuestaRepository propuestaRepository,
            CursoRepository cursoRepository,
            FlowableClient flowableClient,
            TareaService tareaService,
            @Value("${app.almacenamiento.propuestas:almacenamiento/propuestas}") String rutaPropuestas
    ) {
        this.propuestaRepository = propuestaRepository;
        this.cursoRepository = cursoRepository;
        this.flowableClient = flowableClient;
        this.tareaService = tareaService;
        this.directorioPropuestas = Paths.get(rutaPropuestas).toAbsolutePath().normalize();
    }

    @Transactional
    public PropuestaCreadaResponse registrarYCompletar(
            String taskId,
            SesionUsuario sesion,
            NuevaPropuestaRequest request
    ) {
        TareaDetalle detalle = tareaService.detalle(taskId, sesion);
        TareaResumen tarea = detalle.tarea();

        if (!Boolean.TRUE.equals(sesion.esJefe())
                || sesion.grupos() == null
                || !sesion.grupos().contains("Equipo-Administrativo")) {
            throw ApiException.sinPermiso("Solo el Jefe Administrativo puede registrar o revisar propuestas.");
        }
        if (tarea.asignado() == null || !tarea.asignado().equalsIgnoreCase(sesion.usuarioId())) {
            throw ApiException.conflicto("TAREA_NO_ASIGNADA", "Debes reclamar la tarea antes de poder completarla.");
        }

        String clave = tarea.taskDefinitionKey();
        if (!TAREAS_PROPUESTA.contains(clave)) {
            throw ApiException.conflicto(
                    "ESTADO_INCOMPATIBLE",
                    "La tarea '" + clave + "' no admite carga de propuesta."
            );
        }

        UUID cursoId = UUID.fromString(tarea.cursoId());
        Curso curso = cursoRepository.findById(cursoId)
                .orElseThrow(() -> ApiException.noEncontrado("No existe el curso asociado a la tarea."));

        BigDecimal importe = parsearImporteValido(request.presupuesto().importe());
        String moneda = request.presupuesto().moneda().trim().toUpperCase();

        List<Propuesta> existentes = propuestaRepository.findByCursoIdOrderByVersionAsc(cursoId);
        int ultimaVersion = existentes.isEmpty() ? 0 : existentes.getLast().getVersion();

        if ("registrar_elaborar_propuesta".equals(clave) && ultimaVersion > 0) {
            throw ApiException.conflicto(
                    "VERSION_DESACTUALIZADA",
                    "Ya existe una propuesta inicial registrada para este curso."
            );
        }

        if ("revisar_propuesta".equals(clave)) {
            if (existentes.isEmpty()) {
                throw ApiException.conflicto("ESTADO_INCOMPATIBLE", "No existe una propuesta previa para revisar.");
            }
            Propuesta ultima = existentes.getLast();
            if (ultima.getEstado() != Propuesta.Estado.MODIFICACIONES) {
                throw ApiException.conflicto(
                        "VERSION_DESACTUALIZADA",
                        "La propuesta vigente no está pendiente de revisión o ya fue actualizada."
                );
            }
            if (request.versionBase() != null && !request.versionBase().equals(ultimaVersion)) {
                throw ApiException.conflicto(
                        "VERSION_DESACTUALIZADA",
                        "La versión base enviada (" + request.versionBase() + ") no coincide con la vigente (" + ultimaVersion + ")."
                );
            }
        }

        int nuevaVersion = ultimaVersion + 1;
        OffsetDateTime ahora = OffsetDateTime.now();

        byte[] pdfBytes = generarPdfBytes(curso, nuevaVersion, request.programa().trim(), importe, moneda, request.fechasPosibles(), ahora);
        Path archivoDestino = guardarPdfEnDisco(cursoId, nuevaVersion, pdfBytes);

        Propuesta nueva = new Propuesta();
        nueva.setCurso(curso);
        nueva.setVersion(nuevaVersion);
        nueva.setPrograma(request.programa().trim());
        nueva.setPresupuestoImporte(importe);
        nueva.setPresupuestoMoneda(moneda);
        nueva.setFechasPosibles(List.copyOf(request.fechasPosibles()));
        nueva.setEstado(Propuesta.Estado.ENVIADA);
        nueva.setPdfRuta(archivoDestino.toString());

        propuestaRepository.saveAndFlush(nueva);

        Map<String, Object> variables = new HashMap<>();
        variables.put("propuestaVersion", nuevaVersion);
        variables.put("recordatorios", 0);

        flowableClient.completar(taskId, variables);

        return new PropuestaCreadaResponse(
                UUID.nameUUIDFromBytes(("propuesta-" + nueva.getId()).getBytes(StandardCharsets.UTF_8)),
                nuevaVersion,
                true
        );
    }

    @Transactional(readOnly = true)
    public List<PropuestaItemResponse> listarPorCurso(UUID cursoId, SesionUsuario sesion) {
        verificarAccesoACurso(cursoId, sesion);

        return propuestaRepository.findByCursoIdOrderByVersionAsc(cursoId).stream()
                .sorted(Comparator.comparingInt(Propuesta::getVersion).reversed())
                .map(PropuestaItemResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public byte[] obtenerPdf(UUID cursoId, int version, SesionUsuario sesion) {
        verificarAccesoACurso(cursoId, sesion);

        Propuesta propuesta = propuestaRepository.findByCursoIdAndVersion(cursoId, version)
                .orElseThrow(() -> ApiException.noEncontrado("No existe la versión solicitada."));

        if (propuesta.getPdfRuta() == null || propuesta.getPdfRuta().isBlank()) {
            throw ApiException.noEncontrado("La propuesta no tiene un PDF asociado.");
        }

        Path ruta = Paths.get(propuesta.getPdfRuta()).toAbsolutePath().normalize();
        if (!ruta.startsWith(directorioPropuestas) || !Files.exists(ruta)) {
            throw ApiException.noEncontrado("El archivo PDF no se encuentra disponible.");
        }

        try {
            return Files.readAllBytes(ruta);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_LECTURA_PDF", "No se pudo leer el archivo PDF.");
        }
    }

    private void verificarAccesoACurso(UUID cursoId, SesionUsuario sesion) {
        Curso curso = cursoRepository.findById(cursoId)
                .orElseThrow(() -> ApiException.noEncontrado("Curso no encontrado."));

        if (sesion.esEmpresa()) {
            Long empresaCursoId = curso.getEmpresa() != null ? curso.getEmpresa().getId() : null;
            if (empresaCursoId == null || !empresaCursoId.equals(sesion.empresaIdNumerico())) {
                throw ApiException.noEncontrado("Curso no encontrado.");
            }
        }
    }

    private BigDecimal parsearImporteValido(String raw) {
        try {
            BigDecimal valor = new BigDecimal(raw.trim()).setScale(2, RoundingMode.HALF_UP);
            if (valor.compareTo(BigDecimal.ZERO) <= 0) {
                throw ApiException.validacion("El importe del presupuesto debe ser mayor a cero.");
            }
            return valor;
        } catch (NumberFormatException ex) {
            throw ApiException.validacion("Formato de importe inválido.");
        }
    }

    private Path guardarPdfEnDisco(UUID cursoId, int version, byte[] contenido) {
        try {
            Files.createDirectories(directorioPropuestas);
            Path destino = directorioPropuestas.resolve(cursoId + "-v" + version + ".pdf").normalize();
            if (!destino.startsWith(directorioPropuestas)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "RUTA_INVALIDA", "Nombre de archivo inválido.");
            }
            Files.write(destino, contenido);
            return destino;
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_GENERACION_PDF", "No se pudo guardar el PDF de la propuesta.");
        }
    }

    private byte[] generarPdfBytes(
            Curso curso,
            int version,
            String programa,
            BigDecimal importe,
            String moneda,
            List<OffsetDateTime> fechasPosibles,
            OffsetDateTime fechaEmision
    ) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 48, 48, 54, 48);
            PdfWriter.getInstance(document, out);
            document.open();

            Font tituloFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(24, 48, 89));
            Font subtituloFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.DARK_GRAY);
            Font seccionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, new Color(24, 48, 89));
            Font textoFont = FontFactory.getFont(FontFactory.HELVETICA, 10.5f, Color.BLACK);
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10.5f, Color.BLACK);

            Paragraph header = new Paragraph("PROPUESTA DE CAPACITACIÓN - VERSIÓN " + version, tituloFont);
            header.setSpacingAfter(4f);
            document.add(header);

            Paragraph meta = new Paragraph(
                    "Dirección de Vinculación | Emitida: " + fechaEmision.format(FECHA_FMT),
                    subtituloFont
            );
            meta.setSpacingAfter(16f);
            document.add(meta);

            PdfPTable tablaDatos = new PdfPTable(2);
            tablaDatos.setWidthPercentage(100);
            tablaDatos.setWidths(new float[]{32f, 68f});
            tablaDatos.setSpacingAfter(16f);

            agregarFilaTabla(tablaDatos, "Curso / Temática:", curso.getTematica() != null ? curso.getTematica() : "-", boldFont, textoFont);
            agregarFilaTabla(tablaDatos, "Empresa solicitante:",
                    curso.getEmpresa() != null && curso.getEmpresa().getNombre() != null
                            ? curso.getEmpresa().getNombre() : "-", boldFont, textoFont);
            agregarFilaTabla(tablaDatos, "Versión de propuesta:", "v" + version, boldFont, textoFont);
            agregarFilaTabla(tablaDatos, "Presupuesto estimado:", moneda + " " + importe.toPlainString(), boldFont, textoFont);

            document.add(tablaDatos);

            Paragraph secPrograma = new Paragraph("1. Programa y contenidos propuestos", seccionFont);
            secPrograma.setSpacingAfter(6f);
            document.add(secPrograma);

            Paragraph cuerpoPrograma = new Paragraph(programa, textoFont);
            cuerpoPrograma.setAlignment(Element.ALIGN_JUSTIFIED);
            cuerpoPrograma.setSpacingAfter(16f);
            document.add(cuerpoPrograma);

            Paragraph secFechas = new Paragraph("2. Fechas posibles sugeridas", seccionFont);
            secFechas.setSpacingAfter(6f);
            document.add(secFechas);

            for (OffsetDateTime fecha : fechasPosibles) {
                Paragraph item = new Paragraph("•  " + fecha.format(FECHA_FMT), textoFont);
                item.setIndentationLeft(12f);
                item.setSpacingAfter(3f);
                document.add(item);
            }

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_GENERACION_PDF", "Error al generar el documento PDF.");
        }
    }

    private void agregarFilaTabla(PdfPTable tabla, String etiqueta, String valor, Font labelFont, Font valFont) {
        PdfPCell c1 = new PdfPCell(new Phrase(etiqueta, labelFont));
        c1.setBackgroundColor(new Color(242, 245, 250));
        c1.setPadding(7f);
        c1.setBorderColor(new Color(210, 218, 230));

        PdfPCell c2 = new PdfPCell(new Phrase(valor, valFont));
        c2.setPadding(7f);
        c2.setBorderColor(new Color(210, 218, 230));

        tabla.addCell(c1);
        tabla.addCell(c2);
    }
}