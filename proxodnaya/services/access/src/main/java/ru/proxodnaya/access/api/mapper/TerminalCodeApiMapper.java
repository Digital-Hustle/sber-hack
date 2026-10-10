package ru.proxodnaya.access.api.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import ru.proxodnaya.access.api.dto.response.TerminalCodeHashRs;
import ru.proxodnaya.access.api.dto.response.TerminalCodeHashesRs;
import ru.proxodnaya.access.api.dto.response.TerminalCodeInfoRs;
import ru.proxodnaya.access.api.dto.response.TerminalCodesRs;
import ru.proxodnaya.access.features.terminalcode.model.TerminalCode;

/**
 * Отделяет публичные свойства от проверочных хэшей.
 */
@Component
public class TerminalCodeApiMapper {

    private static final String SERVICE = "service";
    private static final String GUARD = "guard";

    /**
     * Код для кабинета, без секретов.
     *
     * @param code запись или null
     * @return свойства или null
     */
    public TerminalCodeInfoRs convert(TerminalCode code) {
        return code == null ? null : new TerminalCodeInfoRs(code.kind(), code.digits(), code.updatedAt().toEpochMilli(), code.by());
    }

    /**
     * Обёртка публичных свойств.
     *
     * @param codes записи
     * @return состояние кодов
     */
    public TerminalCodesRs convert(List<TerminalCode> codes) {
        return new TerminalCodesRs(convert(find(codes, SERVICE)), convert(find(codes, GUARD)));
    }

    /**
     * Проверочные хэши для сопряжённого киоска.
     *
     * @param codes записи
     * @return хэши
     */
    public TerminalCodeHashesRs convertHashes(List<TerminalCode> codes) {
        return new TerminalCodeHashesRs(convertHash(find(codes, SERVICE)), convertHash(find(codes, GUARD)));
    }

    private static TerminalCodeHashRs convertHash(TerminalCode code) {
        return code == null ? null : new TerminalCodeHashRs(code.salt(), code.hash(), code.iter(), code.digits(),
                code.updatedAt().toEpochMilli(), code.by());
    }

    private static TerminalCode find(List<TerminalCode> codes, String kind) {
        return codes.stream().filter(code -> kind.equals(code.kind())).findFirst().orElse(null);
    }
}
