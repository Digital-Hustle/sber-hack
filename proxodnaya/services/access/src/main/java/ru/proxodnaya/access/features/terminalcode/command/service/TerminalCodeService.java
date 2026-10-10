package ru.proxodnaya.access.features.terminalcode.command.service;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.List;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.proxodnaya.access.features.terminalcode.command.repository.TerminalCodeRepository;
import ru.proxodnaya.access.features.terminalcode.model.TerminalCode;
import ru.proxodnaya.common.exception.DomainException;
import ru.proxodnaya.common.exception.ErrorCode;
import ru.proxodnaya.common.exception.NotFoundException;

/**
 * Солёные PBKDF2-хэши для локальной сервисной панели и снимка офлайн-режима.
 */
@Service
public class TerminalCodeService {

    private static final int ITERATIONS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private final TerminalCodeRepository repository;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public TerminalCodeService(TerminalCodeRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Читает сохранённые коды.
     *
     * @return хэши; доступ к ним определяет контроллер
     */
    public List<TerminalCode> current() {
        return repository.findAll();
    }

    /**
     * Меняет код с проверкой слабых сочетаний и случайной солью.
     *
     * @param kind    service или guard
     * @param rawCode 4–6 цифр
     * @param updatedBy автор из JWT
     * @return только сохранённый хэш
     * @throws NotFoundException неизвестный вид
     * @throws DomainException слабый код
     * @throws IllegalStateException алгоритм недоступен
     */
    @Transactional
    public TerminalCode update(String kind, String rawCode, String updatedBy) {
        if (!List.of("service", "guard").contains(kind)) {
            throw new NotFoundException("Неизвестный вид кода терминала");
        }
        if (weak(rawCode)) {
            throw new DomainException(ErrorCode.CODE_WEAK, "Слабый код — выберите 4–6 цифр без повторов, последовательности и года");
        }
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        PBEKeySpec spec = new PBEKeySpec(rawCode.toCharArray(), salt, ITERATIONS, HASH_BITS);
        try {
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            TerminalCode code = new TerminalCode(kind, encode(salt), encode(hash), ITERATIONS, rawCode.length(),
                    clock.instant(), updatedBy);
            repository.save(code);
            return code;
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("PBKDF2 недоступен", error);
        } finally {
            spec.clearPassword();
        }
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static boolean weak(String value) {
        if (value == null || !value.matches("[0-9]{4,6}") || value.matches("([0-9])\\1+") || value.matches("(19|20)[0-9]{2}")
                || "2580".equals(value)) {
            return true;
        }
        int step = value.charAt(1) - value.charAt(0);
        boolean sequence = Math.abs(step) == 1;
        for (int digit = 1; sequence && digit < value.length(); digit++) {
            sequence = value.charAt(digit) - value.charAt(digit - 1) == step;
        }
        return sequence;
    }
}
