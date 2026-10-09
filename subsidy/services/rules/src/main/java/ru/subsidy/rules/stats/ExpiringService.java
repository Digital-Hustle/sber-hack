package ru.subsidy.rules.stats;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.subsidy.rules.config.RulesProperties;
import ru.subsidy.rules.engine.Docs;
import ru.subsidy.rules.facts.DocFacts;
import ru.subsidy.rules.facts.FactsAssembler;
import ru.subsidy.rules.facts.PackageFacts;
import ru.subsidy.rules.jooq.Tables;
import ru.subsidy.rules.program.Program;
import ru.subsidy.rules.program.ProgramService;

/**
 * Истекающие документы (B-05): выписка ЕГРЮЛ и справка ФНС по пакетам без решения.
 */
@Service
public class ExpiringService {

    private static final List<Source> SOURCES = List.of(
            new Source(Docs.EGRUL, "extractDate", "egrulMaxAgeDays"),
            new Source(Docs.TAX, "certificateDate", "taxMaxAgeDays"));

    private final DSLContext dsl;
    private final FactsAssembler assembler;
    private final ProgramService programs;
    private final Clock clock;
    private final RulesProperties props;

    /**
     * Конструктор.
     *
     * @param dsl jOOQ
     * @param assembler факты
     * @param programs программы
     * @param clock часы
     * @param props настройки
     */
    public ExpiringService(DSLContext dsl, FactsAssembler assembler, ProgramService programs, Clock clock,
                           RulesProperties props) {
        this.dsl = dsl;
        this.assembler = assembler;
        this.programs = programs;
        this.clock = clock;
        this.props = props;
    }

    /**
     * Документы, срок которых истекает в ближайшие days дней (или уже истёк).
     *
     * @param days горизонт или null (по умолчанию из настроек)
     * @return по возрастанию оставшихся дней
     */
    @Transactional(readOnly = true)
    public List<Expiring> expiring(Integer days) {
        int horizon = days == null ? props.expiringDays() : days;
        LocalDate today = LocalDate.now(clock.withZone(props.zone()));
        List<Expiring> out = new ArrayList<>();
        List<UUID> open = dsl.select(Tables.PACKAGE_VIEW.PACKAGE_ID).from(Tables.PACKAGE_VIEW)
                .where(Tables.PACKAGE_VIEW.STATUS.ne("DECIDED")).fetch(Tables.PACKAGE_VIEW.PACKAGE_ID);
        for (UUID packageId : open) {
            Optional<PackageFacts> facts = assembler.assemble(packageId);
            Optional<Program> program = facts.flatMap(found -> programs.find(found.programCode()));
            if (facts.isPresent() && program.isPresent()) {
                collect(facts.get(), program.get(), today, horizon, out);
            }
        }
        out.sort(Comparator.comparingLong(Expiring::daysLeft));
        return out;
    }

    private static void collect(PackageFacts facts, Program program, LocalDate today, int horizon,
                                List<Expiring> out) {
        for (Source source : SOURCES) {
            Optional<Integer> maxAge = program.params().integer(source.param());
            for (DocFacts doc : facts.ofType(source.type())) {
                Optional<LocalDate> date = doc.date(source.field());
                if (date.isPresent() && maxAge.isPresent()) {
                    LocalDate expires = date.get().plusDays(maxAge.get());
                    long left = ChronoUnit.DAYS.between(today, expires);
                    if (left <= horizon) {
                        out.add(new Expiring(facts.packageId(), facts.inn(), doc.documentId(), source.type(),
                                Docs.title(source.type()), date.get(), expires, left));
                    }
                }
            }
        }
    }

    /**
     * Истекающий документ.
     *
     * @param packageId пакет
     * @param inn ИНН организации
     * @param documentId документ
     * @param type вид
     * @param title название
     * @param date дата документа
     * @param expiresAt истекает
     * @param daysLeft дней осталось (меньше нуля — истёк)
     */
    public record Expiring(UUID packageId, String inn, UUID documentId, String type, String title, LocalDate date,
                           LocalDate expiresAt, long daysLeft) {
    }

    /**
     * Откуда брать дату и срок.
     *
     * @param type вид
     * @param field поле даты
     * @param param параметр срока
     */
    private record Source(String type, String field, String param) {
    }
}
