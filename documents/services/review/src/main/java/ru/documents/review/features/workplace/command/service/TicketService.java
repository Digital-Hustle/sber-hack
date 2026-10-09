package ru.documents.review.features.workplace.command.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.documents.review.config.ReviewProperties;
import ru.documents.review.features.workplace.command.repository.TicketRepository;

/**
 * Одноразовые билеты для EventSource (заголовок Authorization туда не передать).
 */
@Service
public class TicketService {

    private static final int BYTES = 32;

    private final TicketRepository repo;
    private final ReviewProperties props;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public TicketService(TicketRepository repo, ReviewProperties props, Clock clock) {
        this.repo = repo;
        this.props = props;
        this.clock = clock;
    }

    /**
     * Выдать билет.
     *
     * @param username оператор
     * @return билет
     */
    @Transactional
    public String issue(String username) {
        byte[] b = new byte[BYTES];
        random.nextBytes(b);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        repo.insert(hash(ticket), username, clock.instant().plus(props.ticketTtl()), clock.instant());
        return ticket;
    }

    /**
     * Погасить билет.
     *
     * @param ticket билет
     * @return оператор, если билет действителен
     */
    @Transactional
    public Optional<String> consume(String ticket) {
        return ticket == null ? Optional.empty() : repo.consume(hash(ticket), clock.instant());
    }

    private static String hash(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
