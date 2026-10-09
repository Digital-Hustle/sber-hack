package ru.documents.intake.security;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import ru.documents.intake.shared.Secrets;

/**
 * Аутентификация соискателя: заголовок «Authorization: Applicant &lt;ключ&gt;» → заявка по хэшу ключа (FR-14).
 * Неизвестный ключ — просто не аутентифицирован (дальше 401).
 */
public class ApplicantKeyFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Applicant ";

    private final JdbcClient jdbc;
    private final String pepper;

    public ApplicantKeyFilter(JdbcClient jdbc, String pepper) {
        this.jdbc = jdbc;
        this.pepper = pepper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest rq, HttpServletResponse rs, FilterChain chain)
            throws ServletException, IOException {
        String h = rq.getHeader("Authorization");
        if (h != null && h.startsWith(PREFIX)) {
            String key = h.substring(PREFIX.length()).trim();
            jdbc.sql("select id from applications where access_key_hash = ?")
                    .param(Secrets.hash(pepper, key)).query(UUID.class).optional()
                    .ifPresent(id -> SecurityContextHolder.getContext().setAuthentication(new Token(id)));
        }
        chain.doFilter(rq, rs);
    }

    /** Токен аутентификации соискателя. */
    static final class Token extends AbstractAuthenticationToken {

        private static final long serialVersionUID = 1L;

        private final ApplicantPrincipal principal;

        Token(UUID applicationId) {
            super(List.of(new SimpleGrantedAuthority("ROLE_APPLICANT")));
            this.principal = new ApplicantPrincipal(applicationId);
            setAuthenticated(true);
        }

        @Override
        public Object getCredentials() {
            return "";
        }

        @Override
        public Object getPrincipal() {
            return principal;
        }
    }
}
