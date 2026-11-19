package pe.edu.upc.luzdelsur.incidencias.config;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Compatibilidad con builds del frontend que definen VITE_API_URL sin el
 * sufijo {@code /api} y por eso piden {@code /v1/**} en lugar de
 * {@code /api/v1/**}.
 *
 * <p>La ruta se reescribe antes de que Spring resuelva el controlador y
 * evalue CORS, por lo que no hace falta duplicar los mappings ni relajar los
 * orígenes permitidos. Es un shim temporal: la forma correcta es recompilar
 * el frontend con {@code VITE_API_URL=https://.../api}.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiPathAliasFilter extends OncePerRequestFilter {

    private static final String LEGACY_PREFIX = "/v1";
    private static final String API_PREFIX = "/api/v1";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (LEGACY_PREFIX.equals(uri) || uri.startsWith(LEGACY_PREFIX + "/")) {
            filterChain.doFilter(new AliasedRequest(request), response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static final class AliasedRequest extends HttpServletRequestWrapper {

        private final String aliasedUri;

        private AliasedRequest(HttpServletRequest request) {
            super(request);
            this.aliasedUri = API_PREFIX + request.getRequestURI().substring(LEGACY_PREFIX.length());
        }

        @Override
        public String getRequestURI() {
            return aliasedUri;
        }

        @Override
        public StringBuffer getRequestURL() {
            HttpServletRequest original = (HttpServletRequest) super.getRequest();
            StringBuffer url = original.getRequestURL();
            int start = url.indexOf(original.getRequestURI());
            if (start < 0) {
                return new StringBuffer(aliasedUri);
            }
            return new StringBuffer(url.substring(0, start) + aliasedUri);
        }
    }
}
