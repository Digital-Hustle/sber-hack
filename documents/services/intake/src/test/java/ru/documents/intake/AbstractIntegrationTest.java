package ru.documents.intake;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Ключи шифрования и pepper генерируются на каждый прогон — в репозитории секретов нет. */
public abstract class AbstractIntegrationTest {

    private static final SecureRandom RND = new SecureRandom();

    @DynamicPropertySource
    static void keys(DynamicPropertyRegistry r) {
        byte[] key = new byte[32];
        RND.nextBytes(key);
        byte[] pepper = new byte[24];
        RND.nextBytes(pepper);
        r.add("app.intake.files-key", () -> Base64.getEncoder().encodeToString(key));
        r.add("app.intake.pepper", () -> Base64.getEncoder().encodeToString(pepper));
    }
}
