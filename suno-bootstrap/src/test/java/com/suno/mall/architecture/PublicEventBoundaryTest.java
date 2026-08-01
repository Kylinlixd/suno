package com.suno.mall.architecture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.suno.mall.core.event.DomainEvent;
import com.suno.mall.core.event.EventOutbox;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

@AnalyzeClasses(
        packages = "com.suno.mall",
        importOptions = ImportOption.DoNotIncludeTests.class)
class PublicEventBoundaryTest {

    private static final String DOMAIN_EVENT = "com.suno.mall.core.event.DomainEvent";
    private static final String DOCUMENTED_DOMAIN_EVENT = "com.suno.mall.core.event.DocumentedDomainEvent";
    private static final String USE_CASE_ID = "com.suno.mall.core.event.UseCaseId";
    private static final String EVENT_OUTBOX = "com.suno.mall.core.event.EventOutbox";

    @ArchTest
    static final ArchRule eventOutboxAcceptsOnlyDomainEvents = methods()
            .that().areDeclaredIn(EventOutbox.class)
            .should().haveRawParameterTypes(DomainEvent.class);

    @ArchTest
    static final ArchRule publicEventPortsAcceptOnlyDomainEvents = methods()
            .that().areDeclaredInClassesThat().resideInAnyPackage(
                    "com.suno.mall.identity.api.event..",
                    "com.suno.mall.recycle.api.event..",
                    "com.suno.mall.marketplace.api.event..",
                    "com.suno.mall.payment.api.event..",
                    "com.suno.mall.operations.api.event..")
            .should().haveRawParameterTypes(DomainEvent.class)
            .allowEmptyShould(true);

    @Test
    void coreEventContractIsExplicitAndConstrained() {
        Class<?> domainEvent = load(DOMAIN_EVENT);
        Class<?> documentedDomainEvent = load(DOCUMENTED_DOMAIN_EVENT);
        Class<?> eventOutbox = load(EVENT_OUTBOX);

        assertTrue(domainEvent.isInterface());
        assertTrue(documentedDomainEvent.isInterface());
        assertTrue(domainEvent.isAssignableFrom(documentedDomainEvent));
        assertEquals(Set.of(DOMAIN_EVENT), Arrays.stream(eventOutbox.getDeclaredMethods())
                .map(Method::getParameterTypes)
                .flatMap(Arrays::stream)
                .map(Class::getName)
                .collect(Collectors.toSet()));
    }

    @Test
    void everyConcretePublicEventIsDocumentedAndPublishedThroughDomainEvent() {
        Class<?> domainEvent = load(DOMAIN_EVENT);
        Class<?> documentedDomainEvent = load(DOCUMENTED_DOMAIN_EVENT);
        Class<? extends Annotation> useCaseId = loadAnnotation(USE_CASE_ID);
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.suno.mall");

        classes.stream()
                .filter(candidate -> !candidate.isInterface()
                        && !candidate.isAnnotation()
                        && !candidate.isEnum())
                .filter(candidate -> candidate.isAssignableTo(domainEvent)
                        || candidate.getPackageName().matches(
                                "com\\.suno\\.mall\\.(identity|recycle|marketplace|payment|operations)\\.api\\.event(\\..*)?"))
                .forEach(candidate -> {
                    Class<?> eventType = candidate.reflect();
                    assertTrue(documentedDomainEvent.isAssignableFrom(eventType),
                            () -> eventType.getName() + " must implement DocumentedDomainEvent");
                    assertTrue(eventType.isAnnotationPresent(useCaseId),
                            () -> eventType.getName() + " must carry @UseCaseId");
                    if (belongsToFeatureModule(eventType)) {
                        assertTrue(eventType.getPackageName().matches(
                                        "com\\.suno\\.mall\\.(identity|recycle|marketplace|payment|operations)\\.api\\.event(\\..*)?"),
                                () -> eventType.getName() + " must be exposed from its module api.event package");
                    }
                });

        classes.stream()
                .filter(JavaClass::isInterface)
                .filter(candidate -> candidate.getPackageName().matches(
                        "com\\.suno\\.mall\\.(identity|recycle|marketplace|payment|operations)\\.api\\.event(\\..*)?"))
                .forEach(candidate -> Arrays.stream(candidate.reflect().getDeclaredMethods())
                        .forEach(method -> assertEquals(
                                Set.of(DOMAIN_EVENT),
                                Arrays.stream(method.getParameterTypes())
                                        .map(Class::getName)
                                        .collect(Collectors.toSet()),
                                () -> method + " must accept only DomainEvent")));
    }

    private static boolean belongsToFeatureModule(Class<?> type) {
        return type.getPackageName().matches(
                "com\\.suno\\.mall\\.(identity|recycle|marketplace|payment|operations)(\\..*)?");
    }

    private static Class<?> load(String typeName) {
        return assertDoesNotThrow(() -> Class.forName(typeName));
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Annotation> loadAnnotation(String typeName) {
        Class<?> type = load(typeName);
        assertTrue(type.isAnnotation());
        return (Class<? extends Annotation>) type;
    }
}
