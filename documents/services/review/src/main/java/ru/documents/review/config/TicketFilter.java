package ru.documents.review.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.documents.review.features.workplace.command.service.TicketService;

/**
 * Аутентификация SSE по одноразовому билету из /stream/ticket (выдаётся только по JWT оператора).
 */
public class TicketFilter extends OncePerRequestFilter {

    /** Роль, которую получает запрос с действительным билетом. */
    public static final String ROLE = "STREAM";

    private final TicketService tickets;

    public TicketFilter(TicketService tickets) {
        this.tickets = tickets;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("GET".equals(request.getMethod()) && "/api/v1/stream".equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        tickets.consume(request.getParameter("ticket")).ifPresent(user -> {
            var auth = new UsernamePasswordAuthenticationToken(user, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + ROLE)));
            SecurityContextHolder.getContext().setAuthentication(auth);
        });
        chain.doFilter(request, response);
    }
}
