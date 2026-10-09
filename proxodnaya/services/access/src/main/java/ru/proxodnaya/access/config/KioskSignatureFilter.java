package ru.proxodnaya.access.config;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import ru.proxodnaya.access.features.kiosk.api.KioskSignature;
import ru.proxodnaya.access.features.kiosk.command.service.KioskAuthenticator;
import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.ErrorCode;
import ru.proxodnaya.common.exception.ProblemDetailFactory;

/**
 * Запросы терминала {@code /api/v1/kiosk/**} с заголовком {@code X-Kiosk-Id}: проверяет подпись (ACC-12) и ставит
 * {@link KioskAuthentication}. Без заголовка запрос идёт дальше — к JWT (охранник с токеном).
 */
@Slf4j
public class KioskSignatureFilter extends OncePerRequestFilter {

    static final String KIOSK_ID = "X-Kiosk-Id";
    private static final String KIOSK_PATH = "/api/v1/kiosk/";

    private final KioskAuthenticator authenticator;
    private final ObjectMapper objectMapper;
    private final String pathPrefix;

    public KioskSignatureFilter(KioskAuthenticator authenticator, ObjectMapper objectMapper, String pathPrefix) {
        this.authenticator = authenticator;
        this.objectMapper = objectMapper;
        this.pathPrefix = pathPrefix;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getHeader(KIOSK_ID) == null || !request.getRequestURI().startsWith(KIOSK_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        byte[] body = request.getInputStream().readAllBytes();
        KioskSignature signature = new KioskSignature(request.getHeader(KIOSK_ID), request.getHeader("X-Ts"),
                request.getHeader("X-Nonce"), request.getHeader("X-Signature"));
        String path = KioskAuthenticator.signedPath(pathPrefix, request.getRequestURI(), request.getQueryString());
        try {
            KioskAuthentication auth = new KioskAuthentication(authenticator.authenticate(signature, request.getMethod(), path, body));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
        } catch (DomainException error) {
            log.warn("kiosk request rejected kioskId={} code={}", signature.kioskId(), error.code());
            reject(response, error);
            return;
        }
        chain.doFilter(new CachedBodyRequest(request, body), response);
    }

    private void reject(HttpServletResponse response, DomainException error) throws IOException {
        HttpStatus status = error.code() == ErrorCode.KIOSK_UNKNOWN ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN;
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ProblemDetail problem = ProblemDetailFactory.of(error, status.value());
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    /**
     * Тело уже прочитано для подписи — отдаём его контроллеру ещё раз.
     */
    private static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream source = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return source.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("async read");
                }

                @Override
                public int read() {
                    return source.read();
                }

                @Override
                public int read(byte[] buffer, int off, int len) {
                    return source.read(buffer, off, len);
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
