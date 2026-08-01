package com.suno.mall.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
        packages = "com.suno.mall",
        importOptions = ImportOption.DoNotIncludeTests.class)
class LayerBoundaryTest {

    @ArchTest
    static final ArchRule domainMustNotDependOnFrameworkOrTransport = noClasses()
            .that().resideInAnyPackage("com.suno.mall..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "jakarta.servlet..",
                    "com.fasterxml.jackson..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule migratedControllersMustNotUseRepositoriesDirectly = noClasses()
            .that().resideInAnyPackage(
                    "com.suno.mall.identity..controller..",
                    "com.suno.mall.recycle..controller..",
                    "com.suno.mall.marketplace..controller..",
                    "com.suno.mall.payment..controller..",
                    "com.suno.mall.operations..controller..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.suno.mall..repository..", "com.suno.mall..dao..")
            .allowEmptyShould(true);
}
