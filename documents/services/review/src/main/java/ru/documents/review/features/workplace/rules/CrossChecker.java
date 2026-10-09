package ru.documents.review.features.workplace.rules;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import ru.documents.review.features.workplace.model.Finding;

/**
 * Сверка между документами одного человека (B-03) и комплектности (B-01).
 * Эталон — паспорт (иностранный или РФ); если его нет — первый документ с данными.
 */
public final class CrossChecker {

    private static final List<String> REFERENCE = List.of("PASSPORT_FOREIGN", "PASSPORT_RF");
    private static final Set<String> EXPIRY_FIELDS = Set.of("expiryDate", "stayUntil");
    private static final int MIN_NUMBER = 6;

    private CrossChecker() {
    }

    /**
     * Замечания по комплекту.
     *
     * @param docs     документы с итоговыми значениями
     * @param required обязательные виды по анкете
     * @return замечания
     */
    public static List<Finding> check(List<DocFacts> docs, Collection<String> required) {
        List<Finding> out = new ArrayList<>();
        Set<String> present = docs.stream().map(DocFacts::type).filter(t -> t != null).collect(Collectors.toSet());
        for (String t : required) {
            if (!present.contains(t)) {
                out.add(new Finding("MISSING_DOCUMENT", null, List.of(), Map.of("type", t)));
            }
        }
        Optional<DocFacts> ref = docs.stream().filter(d -> isReference(d) && d.fullName() != null).findFirst()
                .or(() -> docs.stream().filter(d -> d.fullName() != null).findFirst());
        if (ref.isEmpty()) {
            return out;
        }
        DocFacts a = ref.get();
        for (DocFacts b : docs) {
            if (b.id().equals(a.id())) {
                continue;
            }
            names(a, b, out);
            if (a.get("birthDate") != null && b.get("birthDate") != null
                    && !a.get("birthDate").equals(b.get("birthDate"))) {
                out.add(mismatch("Дата рождения", "birthDate", a, b));
            }
            String pa = a.get("documentNumber") != null ? a.get("documentNumber") : passportRf(a);
            if (pa != null && b.get("passportNumber") != null && !sameNumber(pa, b.get("passportNumber"))) {
                out.add(mismatch("Номер паспорта", "passportNumber", a, b));
            }
        }
        return out;
    }

    /**
     * Дата окончания действия документа (B-04): срок действия или срок пребывания.
     *
     * @param d документ
     * @return дата
     */
    public static Optional<LocalDate> expiresOn(DocFacts d) {
        for (String f : EXPIRY_FIELDS) {
            String v = d.get(f);
            if (v != null) {
                try {
                    return Optional.of(LocalDate.parse(v));
                } catch (java.time.format.DateTimeParseException e) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    /**
     * ФИО соискателя для очереди: из паспорта, иначе из любого документа.
     *
     * @param docs документы
     * @return ФИО или null
     */
    public static String applicantName(List<DocFacts> docs) {
        return docs.stream().filter(d -> isReference(d) && d.fullName() != null).map(DocFacts::fullName)
                .findFirst()
                .orElseGet(() -> docs.stream().map(DocFacts::fullName).filter(n -> n != null).findFirst().orElse(null));
    }

    /** Паспорт — эталон ФИО; вид может быть ещё не определён (null), а List.of(...).contains(null) бросает NPE. */
    private static boolean isReference(DocFacts d) {
        return d.type() != null && REFERENCE.contains(d.type());
    }

    private static void names(DocFacts a, DocFacts b, List<Finding> out) {
        String na = a.fullName();
        if (na == null) {
            return;
        }
        String nb = b.fullName();
        if (nb != null && !NameMatcher.same(na, nb)) {
            out.add(mismatch("ФИО", "fullName", a, b));
            return;
        }
        String latin = b.get("fullNameLatin");
        if (latin != null && !NameMatcher.same(na, latin)) {
            out.add(mismatch("ФИО латиницей", "fullNameLatin", a, b));
        }
    }

    private static String passportRf(DocFacts a) {
        if (!"PASSPORT_RF".equals(a.type()) || a.get("number") == null) {
            return null;
        }
        return (a.get("series") == null ? "" : a.get("series")) + a.get("number");
    }

    static boolean sameNumber(String x, String y) {
        String a = x.replaceAll("[^\\p{L}\\p{N}]", "").toUpperCase(Locale.ROOT);
        String b = y.replaceAll("[^\\p{L}\\p{N}]", "").toUpperCase(Locale.ROOT);
        if (a.equals(b)) {
            return true;
        }
        String shorter = a.length() <= b.length() ? a : b;
        String longer = a.length() <= b.length() ? b : a;
        return shorter.length() >= MIN_NUMBER && longer.endsWith(shorter);
    }

    private static Finding mismatch(String title, String field, DocFacts a, DocFacts b) {
        return new Finding("CROSS_MISMATCH", field, List.of(a.id(), b.id()),
                Map.of("field", title, "a", name(a), "b", name(b)));
    }

    private static String name(DocFacts d) {
        return d.typeName() != null ? d.typeName() : String.valueOf(d.type());
    }
}
