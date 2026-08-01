package com.suno.mall.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
        packages = "com.suno.mall",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryTest {

    @ArchTest
    static final ArchRule featureModulesMustNotDependOnBootstrapLegacyPackages = noClasses()
            .that().resideInAnyPackage(
                    "com.suno.mall.identity..",
                    "com.suno.mall.recycle..",
                    "com.suno.mall.marketplace..",
                    "com.suno.mall.payment..",
                    "com.suno.mall.operations..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.suno.mall.controller..",
                    "com.suno.mall.dao..",
                    "com.suno.mall.entity..",
                    "com.suno.mall.service..",
                    "com.suno.mall.config..",
                    "com.suno.mall.provider..",
                    "com.suno.mall.dto..");

    @ArchTest
    static final ArchRule identityMayUseOnlyCore = classes()
            .that().resideInAPackage("com.suno.mall.identity..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                    "com.suno.mall.identity..", "com.suno.mall.core..", "java..", "javax..", "jakarta..", "org..", "com.fasterxml..", "io..");

    @ArchTest
    static final ArchRule recycleMayUseOnlyCore = classes()
            .that().resideInAPackage("com.suno.mall.recycle..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                    "com.suno.mall.recycle..", "com.suno.mall.core..", "java..", "javax..", "jakarta..", "org..", "com.fasterxml..", "io..");

    @ArchTest
    static final ArchRule marketplaceMayUseOnlyRecycleApi = classes()
            .that().resideInAPackage("com.suno.mall.marketplace..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                    "com.suno.mall.marketplace..", "com.suno.mall.recycle.api..", "com.suno.mall.core..", "java..", "javax..", "jakarta..", "org..", "com.fasterxml..", "io..");

    @ArchTest
    static final ArchRule paymentMayUseOnlyMarketplaceApi = classes()
            .that().resideInAPackage("com.suno.mall.payment..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                    "com.suno.mall.payment..", "com.suno.mall.marketplace.api..", "com.suno.mall.core..", "java..", "javax..", "jakarta..", "org..", "com.fasterxml..", "io..");
}
