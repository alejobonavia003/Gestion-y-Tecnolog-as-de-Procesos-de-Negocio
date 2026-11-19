package ar.edu.cursos.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Protección CSRF basada en el patrón Double Submit Cookie:
 * - Emite la cookie XSRF-TOKEN (HttpOnly=false, SameSite=Lax) para que la web pueda leerla.
 * - En peticiones mutables desde navegador (o cuando se envía el header X-XSRF-TOKEN),
 *   verifica que el header X-XSRF-TOKEN coincida con el valor de la cookie XSRF-TOKEN.
 */
@Component
public class CsrfFilter extends OncePerRequestFilter {

    public static final String COOKIE_NAME = "XSRF-TOKEN";
    public static final String HEADER_NAME = "X-XSRF-TOKEN";

    private static final Set<String> METODOS_SEGUROS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String tokenCookie = leerCookie(request, COOKIE_NAME);
        if (tokenCookie == null || tokenCookie.isBlank()) {
            tokenCookie = UUID.randomUUID().toString();
            ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, tokenCookie)
                    .path("/")
                    .httpOnly(false)
                    .sameSite("Lax")
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        }

        if (requiereValidacionCsrf(request, tokenCookie)) {
            String tokenHeader = request.getHeader(HEADER_NAME);
            String cookieRecibida = leerCookie(request, COOKIE_NAME);
            if (cookieRecibida == null || tokenHeader == null || !cookieRecibida.equals(tokenHeader.trim())) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.getWriter().write("{\"codigo\":\"SIN_PERMISO\",\"mensaje\":\"Token CSRF inválido o faltante.\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean requiereValidacionCsrf(HttpServletRequest request, String tokenActivo) {
        String metodo = request.getMethod();
        if (metodo == null || METODOS_SEGUROS.contains(metodo.toUpperCase())) {
            return false;
        }
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/api/v1/simulacion/")) {
            return false;
        }
        String cookieRecibida = leerCookie(request, COOKIE_NAME);
        if (cookieRecibida == null || cookieRecibida.isBlank()) {
            return false;
        }
        boolean tieneHeaderCsrf = request.getHeader(HEADER_NAME) != null;
        boolean esPeticionNavegador = request.getHeader("Origin") != null
                || request.getHeader("Sec-Fetch-Mode") != null;
        return tieneHeaderCsrf || esPeticionNavegador;
    }

    private String leerCookie(HttpServletRequest request, String nombre) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (nombre.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                return c.getValue();
            }
        }
        return null;
    }
}
