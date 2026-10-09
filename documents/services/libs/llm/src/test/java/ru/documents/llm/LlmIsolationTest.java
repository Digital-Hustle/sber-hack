package ru.documents.llm;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

/** FR-40: о провайдерах знает только infra; публичный API не зависит от реализации. */
class LlmIsolationTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("ru.documents.llm");

    @Test
    void apiDoesNotDependOnCoreOrInfra() {
        ArchRuleDefinition.noClasses().that().resideInAPackage("ru.documents.llm.api..")
                .should().dependOnClassesThat().resideInAnyPackage("ru.documents.llm.core..", "ru.documents.llm.infra..")
                .check(CLASSES);
    }

    @Test
    void onlyAutoConfigurationKnowsAdapters() {
        ArchRuleDefinition.noClasses().that().resideOutsideOfPackage("ru.documents.llm.infra..")
                .and().doNotHaveSimpleName("LlmAutoConfiguration")
                .should().dependOnClassesThat().resideInAPackage("ru.documents.llm.infra..")
                .check(CLASSES);
    }

    @Test
    void adaptersDoNotKnowEachOther() {
        for (String p : new String[] {"gigachat", "ollama", "openai"}) {
            ArchRuleDefinition.noClasses().that().resideInAPackage("ru.documents.llm.infra." + p + "..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            java.util.Arrays.stream(new String[] {"gigachat", "ollama", "openai"})
                                    .filter(o -> !o.equals(p)).map(o -> "ru.documents.llm.infra." + o + "..")
                                    .toArray(String[]::new))
                    .check(CLASSES);
        }
    }
}
