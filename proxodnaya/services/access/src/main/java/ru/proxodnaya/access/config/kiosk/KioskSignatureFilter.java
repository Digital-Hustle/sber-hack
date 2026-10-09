package ru.proxodnaya.access.config.kiosk;

import java.io.IOException;
import java.time.Clock;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ru.proxodnaya.access.features.kiosk.model.KioskKey;
import ru.proxodnaya.access.features.kiosk.repository.KioskKeyRepository;

/**
 * Фильтр проверки подписи киоска (FR-64) на эндпоинтах /api/v1/kiosk/**.
 * Заголовки: X-Kiosk-Id, X-Ts, X-Nonce, X-Signature.
 * Подпись проверяется по публичному ключу из kiosk_keys.
 * Если киоск не привязан или подпись невалидна → 403 KIOSK_SIGNATURE_INVALID.
 */
@Component
public class KioskSignatureFilter extends OncePerRequestFilter {

    private final KioskSignatureVerifier verifier;
    private final KioskKeyRepository kioskKeyRepository;
    private final Clock clock;

    public KioskSignatureFilter(KioskSignatureVerifier verifier, KioskKeyRepository kioskKeyRepository, Clock clock) {
        this.verifier = verifier;
        this.kioskKeyRepository = kioskKeyRepository;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/v1/kiosk/")) {
            filterChain.doFilter(request, response);
            return;
        }
        // manual endpoint уже защищён через @PreAuthorize("hasRole('GUARD')") — подпись обязательна на всех kiosk-путях
        String kioskId = request.getHeader("X-Kiosk-Id");
        String ts = request.getHeader("X-Ts");
        String nonce = request.getHeader("X-Nonce");
        String signature = request.getHeader("X-Signature");

        if (kioskId == null || ts == null || nonce == null || signature == null) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Отсутствует подпись киоска\"}");
            return;
        }

        long tsMillis;
        try {
            tsMillis = Long.parseLong(ts);
        } catch (NumberFormatException e) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Некорректная метка времени\"}");
            return;
        }

        Optional<KioskKey> keyOpt = kioskKeyRepository.findActive(kioskId);
        if (keyOpt.isEmpty()) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Киоск не привязан\"}");
            return;
        }

        boolean valid = verifier.verify(kioskId, tsMillis, nonce, signature, keyOpt.get().getPublicKey(), clock.instant());
        if (!valid) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Подпись киоска недействительна\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
