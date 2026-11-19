package ar.edu.cursos.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.Cookie;

class CsrfFilterTest {

    private final CsrfFilter filter = new CsrfFilter();

    @Test
    void getSinCookie_emiteCookieXsrfToken() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/sesiones/actual");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, new MockFilterChain());

        assertEquals(200, res.getStatus());
        String setCookie = res.getHeader("Set-Cookie");
        assertNotNull(setCookie);
        assertTrue(setCookie.contains("XSRF-TOKEN="));
    }

    @Test
    void postDesdeNavegadorSinHeader_devuelve403() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/tareas/t1/reclamo");
        req.setCookies(new Cookie("XSRF-TOKEN", "tok-123"));
        req.addHeader("Origin", "http://localhost:5173");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, new MockFilterChain());

        assertEquals(403, res.getStatus());
        assertTrue(res.getContentAsString().contains("SIN_PERMISO"));
    }

    @Test
    void postDesdeNavegadorConTokenCoincidente_permitePaso() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/tareas/t1/reclamo");
        req.setCookies(new Cookie("XSRF-TOKEN", "tok-123"));
        req.addHeader("Origin", "http://localhost:5173");
        req.addHeader("X-XSRF-TOKEN", "tok-123");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertEquals(200, res.getStatus());
        assertNotNull(chain.getRequest());
    }
}
