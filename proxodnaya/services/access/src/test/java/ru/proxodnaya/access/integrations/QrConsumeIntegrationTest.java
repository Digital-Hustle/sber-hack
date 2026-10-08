package ru.proxodnaya.access.integrations;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import ru.proxodnaya.access.config.AbstractIntegrationTest;
import ru.proxodnaya.access.features.qr.api.QrUses;

/**
 * Инвариант гашения: 20 параллельных транзакций с одним QR — погашено ровно один раз.
 */
class QrConsumeIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private QrUses qrUses;

    @Autowired
    private TransactionTemplate transactions;

    @Test
    void consume_twentyInParallel_exactlyOneWins() throws Exception {
        // given
        String deviceId = "race-" + UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();

        // when
        try (ExecutorService pool = Executors.newFixedThreadPool(20)) {
            for (int idx = 0; idx < 20; idx++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return transactions.execute(status -> qrUses.consume(deviceId, 42L, UUID.randomUUID(), Instant.now()));
                }));
            }
            start.countDown();
        }

        // then
        long winners = 0;
        for (Future<Boolean> result : results) {
            winners += Boolean.TRUE.equals(result.get()) ? 1 : 0;
        }
        assertThat(winners).isEqualTo(1);
    }
}
