package ru.proxodnaya.access.features.terminalcode.command.repository;

import java.time.ZoneOffset;
import java.util.List;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import ru.proxodnaya.access.features.terminalcode.model.TerminalCode;
import ru.proxodnaya.access.jooq.Tables;
import ru.proxodnaya.access.jooq.tables.TerminalCodes;

/**
 * Два глобальных кода терминала; открытый код не хранится.
 */
@Repository
public class TerminalCodeRepository {

    private static final TerminalCodes CODES = Tables.TERMINAL_CODES;
    private final DSLContext dsl;

    public TerminalCodeRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Читает оба кода без кеша: смена видна при следующем пульсе киоска.
     *
     * @return сохранённые хэши
     */
    public List<TerminalCode> findAll() {
        return dsl.selectFrom(CODES).orderBy(CODES.KIND).fetch(row -> new TerminalCode(row.getKind(), row.getSalt(), row.getHash(),
                row.getIterations(), row.getDigits(), row.getUpdatedAt().toInstant(), row.getUpdatedBy()));
    }

    /**
     * Атомарно меняет один код.
     *
     * @param code хэш и автор
     */
    public void save(TerminalCode code) {
        dsl.insertInto(CODES).set(CODES.KIND, code.kind()).set(CODES.SALT, code.salt()).set(CODES.HASH, code.hash())
                .set(CODES.ITERATIONS, code.iter()).set(CODES.DIGITS, code.digits())
                .set(CODES.UPDATED_AT, code.updatedAt().atOffset(ZoneOffset.UTC)).set(CODES.UPDATED_BY, code.by())
                .onConflict(CODES.KIND).doUpdate().set(CODES.SALT, code.salt()).set(CODES.HASH, code.hash())
                .set(CODES.ITERATIONS, code.iter()).set(CODES.DIGITS, code.digits())
                .set(CODES.UPDATED_AT, code.updatedAt().atOffset(ZoneOffset.UTC)).set(CODES.UPDATED_BY, code.by()).execute();
    }
}
