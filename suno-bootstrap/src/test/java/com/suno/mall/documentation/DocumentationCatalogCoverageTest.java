package com.suno.mall.documentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.suno.mall.RecycleMallApplication;
import com.suno.mall.core.event.DocumentedDomainEvent;
import com.suno.mall.core.event.DomainEvent;
import com.suno.mall.core.event.EventVersion;
import com.suno.mall.core.event.UseCaseId;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

@SpringBootTest(
        classes = RecycleMallApplication.class,
        properties = {"spring.profiles.active=test", "payment.callback.secret=test-secret"})
class DocumentationCatalogCoverageTest {

    private static final Path CATALOG = Path.of("..", "docs", "requirements", "use-cases.yaml");
    private static final Path EVENTS = Path.of("..", "docs", "requirements", "public-events.yaml");
    private static final Set<String> REQUIRED = Set.of("id", "kind", "owner", "actor", "trigger", "permission",
            "invariants", "errors", "requirementDoc", "requirementAnchor", "developmentAnchor",
            "implementationStatus", "currentSymbols", "targetPhase", "documentationTask");
    private static final Set<Integer> TASKS = Set.of(9, 10, 11, 12, 13);

    @Autowired
    private RequestMappingHandlerMapping mappings;

    @Test
    void catalogOwnsTheExactApplicationRoutesSchedulersEventsAndTasks() throws Exception {
        List<Map<String, Object>> catalog = yaml(CATALOG);
        assertEquals(122, catalog.size());
        assertEquals(catalog.size(), catalog.stream().map(entry -> string(entry, "id")).collect(Collectors.toSet()).size());
        catalog.forEach(this::assertShape);
        catalog.forEach(entry -> assertEquals(ownerForId(string(entry, "id")), string(entry, "owner"),
                () -> "owner does not match the mandated ID decision group: " + string(entry, "id")));

        List<Map<String, Object>> http = ofKind(catalog, "HTTP");
        assertEquals(93, http.size());
        Set<String> actualRoutes = mappings.getHandlerMethods().entrySet().stream()
                .filter(entry -> isDocumentedApplicationEndpoint(entry.getValue().getBeanType()))
                .flatMap(entry -> entry.getKey().getPatternValues().stream().flatMap(path -> entry.getKey().getMethodsCondition()
                        .getMethods().stream().map(method -> method.name() + " " + path)))
                .collect(Collectors.toSet());
        Set<String> catalogRoutes = http.stream().map(entry -> string(entry, "method") + " " + string(entry, "path"))
                .collect(Collectors.toSet());
        assertEquals(catalogRoutes, actualRoutes);
        assertEquals(http.size(), catalogRoutes.size(), "each route has exactly one owner");

        List<Map<String, Object>> schedulers = ofKind(catalog, "SCHEDULER");
        assertEquals(6, schedulers.size());
        Set<String> actualSchedulers = concreteClasses().stream().flatMap(type -> List.of(type.getDeclaredMethods()).stream()
                .filter(method -> method.isAnnotationPresent(Scheduled.class))
                .map(method -> type.getSimpleName() + "#" + method.getName())).collect(Collectors.toSet());
        Set<String> catalogSchedulers = schedulers.stream().map(entry -> string(entry, "scheduledMethod")).collect(Collectors.toSet());
        assertEquals(catalogSchedulers, actualSchedulers);
        schedulers.forEach(entry -> assertEquals(string(entry, "scheduleProperty"), scheduledProperty(string(entry, "scheduledMethod"))));

        List<Map<String, Object>> events = ofKind(catalog, "EVENT");
        List<Map<String, Object>> registry = yaml(EVENTS);
        assertEquals(23, events.size());
        assertEquals(eventKeys(events), eventKeys(registry));
        assertEventBoundary(events, registry);
        assertEquals(Map.of(9, 25L, 10, 27L, 11, 9L, 12, 41L, 13, 20L), catalog.stream()
                .collect(Collectors.groupingBy(entry -> integer(entry, "documentationTask"), Collectors.counting())));
    }

    private void assertShape(Map<String, Object> entry) {
        assertTrue(entry.keySet().containsAll(REQUIRED), () -> "missing required fields for " + entry);
        assertTrue(TASKS.contains(integer(entry, "documentationTask")));
        assertTrue(Set.of("implemented", "partial", "absent").contains(string(entry, "implementationStatus")));
        assertFalse(list(entry, "currentSymbols").isEmpty());
        assertTrue(integer(entry, "targetPhase") > 0);
        if ("HTTP".equals(string(entry, "kind"))) {
            assertTrue(entry.containsKey("method") && entry.containsKey("path"));
        }
        if ("SCHEDULER".equals(string(entry, "kind"))) {
            assertTrue(entry.containsKey("scheduledMethod") && entry.containsKey("scheduleProperty"));
        }
        assertTrue(entry.containsKey("implementedTests") || entry.containsKey("plannedTests"));
        list(entry, "currentSymbols").forEach(symbol -> assertTrue(symbolExists(String.valueOf(symbol)),
                () -> string(entry, "id") + " unresolved current symbol " + symbol));
        assertImplementedTests(entry);
        for (Object planned : list(entry, "plannedTests")) {
            Map<?, ?> item = (Map<?, ?>) planned;
            assertTrue(String.valueOf(item.get("test")).matches("[A-Za-z_$][A-Za-z0-9_$.]*#[A-Za-z_$][A-Za-z0-9_$]*"));
            assertTrue(Integer.parseInt(String.valueOf(item.get("targetPhase"))) > 0);
        }
    }

    private void assertEventBoundary(List<Map<String, Object>> events, List<Map<String, Object>> registry) {
        Map<String, Map<String, Object>> catalogById = events.stream().collect(Collectors.toMap(entry -> string(entry, "id"), Function.identity()));
        Map<String, Map<String, Object>> registryById = registry.stream().collect(Collectors.toMap(entry -> string(entry, "id"), Function.identity()));
        concreteClasses().stream().filter(DocumentationCatalogCoverageTest::isDiscoveredPublicEvent).forEach(type -> {
            assertTrue(DocumentedDomainEvent.class.isAssignableFrom(type));
            assertTrue(type.isAnnotationPresent(UseCaseId.class));
            assertTrue(type.isAnnotationPresent(EventVersion.class));
            String id = type.getAnnotation(UseCaseId.class).value();
            Map<String, Object> registered = registryById.get(id);
            Map<String, Object> catalogEntry = catalogById.get(id);
            assertNotNull(registered, () -> type.getName() + " is not registered");
            assertNotNull(catalogEntry, () -> type.getName() + " is not catalogued");
            assertEquals(string(registered, "eventType"), type.getSimpleName());
            assertEquals(integer(registered, "version"), type.getAnnotation(EventVersion.class).value());
            assertEquals(ownerFor(type), string(registered, "owner"));
            assertEquals(eventKeys(List.of(registered)), eventKeys(List.of(catalogEntry)));
        });
        events.stream().filter(entry -> "implemented".equals(string(entry, "implementationStatus"))).forEach(entry -> assertEquals(1,
                concreteClasses().stream().filter(type -> type.isAnnotationPresent(UseCaseId.class)
                        && string(entry, "id").equals(type.getAnnotation(UseCaseId.class).value())).count()));
    }

    @Test
    void invalidImplementedTestTargetIsRejected() {
        Map<String, Object> invalid = Map.of("implementedTests", List.of("DocumentationCatalogCoverageTest#missingMethod"));
        assertThrows(AssertionError.class, () -> assertImplementedTests(invalid));
        assertImplementedTests(Map.of("implementedTests", List.of(
                "DocumentationCatalogCoverageTest#invalidImplementedTestTargetIsRejected")));
    }

    private static void assertImplementedTests(Map<String, Object> entry) {
        list(entry, "implementedTests").forEach(test -> assertTrue(testExists(String.valueOf(test)),
                () -> "unresolved implemented test " + test));
    }

    private static boolean isDocumentedApplicationEndpoint(Class<?> type) {
        String packageName = type.getPackageName();
        return !packageName.startsWith("org.springframework") && !packageName.startsWith("org.springframework.boot.actuate")
                && (type.isAnnotationPresent(RestController.class) || type.isAnnotationPresent(Controller.class));
    }

    private static String scheduledProperty(String symbol) {
        String[] parts = symbol.split("#", 2);
        Method method = concreteClasses().stream().filter(type -> type.getSimpleName().equals(parts[0]))
                .flatMap(type -> List.of(type.getDeclaredMethods()).stream()).filter(candidate -> candidate.getName().equals(parts[1]))
                .findFirst().orElseThrow();
        Scheduled annotation = method.getAnnotation(Scheduled.class);
        assertNotNull(annotation, () -> symbol + " must carry @Scheduled");
        String expression = annotation.fixedDelayString();
        java.util.regex.Matcher match = java.util.regex.Pattern.compile("\\$\\{([^}:]+)(?::[^}]*)?}").matcher(expression);
        assertTrue(match.matches(), () -> symbol + " must use a property-backed fixed delay");
        return match.group(1);
    }

    private static boolean isDiscoveredPublicEvent(Class<?> type) {
        return DomainEvent.class.isAssignableFrom(type) || type.getPackageName().matches(
                "com\\.suno\\.mall\\.(identity|recycle|marketplace|payment|operations)\\.api\\.event(\\..*)?");
    }

    private static String ownerFor(Class<?> type) {
        String[] parts = type.getPackageName().split("\\.");
        for (String part : parts) {
            if (Set.of("identity", "recycle", "marketplace", "payment", "operations").contains(part)) {
                return Character.toUpperCase(part.charAt(0)) + part.substring(1);
            }
        }
        throw new AssertionError("public event has no owning feature module: " + type.getName());
    }

    private static String ownerForId(String id) {
        if (id.startsWith("IDN-")) {
            return "Identity";
        }
        if (id.startsWith("PAY-")) {
            return "Payment";
        }
        if (id.startsWith("REC-")) {
            return "Recycle";
        }
        if (id.startsWith("MKT-")) {
            return "Marketplace";
        }
        if (id.startsWith("OPS-")) {
            return "Operations";
        }
        throw new AssertionError("unknown mandated ID group: " + id);
    }

    private static Set<String> eventKeys(List<Map<String, Object>> entries) {
        return entries.stream().map(entry -> String.join("|", string(entry, "id"), string(entry, "eventType"),
                String.valueOf(integer(entry, "version")), string(entry, "owner"))).collect(Collectors.toSet());
    }

    private static List<Map<String, Object>> ofKind(List<Map<String, Object>> catalog, String kind) {
        return catalog.stream().filter(entry -> kind.equals(string(entry, "kind"))).toList();
    }

    private static boolean symbolExists(String symbol) {
        String[] parts = symbol.split("#", 2);
        return parts.length == 2 && concreteClasses().stream().filter(type -> type.getSimpleName().equals(parts[0]))
                .anyMatch(type -> List.of(type.getDeclaredMethods()).stream().anyMatch(method -> method.getName().equals(parts[1])));
    }

    private static boolean testExists(String reference) {
        String[] parts = reference.split("#", 2);
        return parts.length == 2 && testClasses().stream().filter(type -> type.getSimpleName().equals(parts[0]))
                .anyMatch(type -> List.of(type.getDeclaredMethods()).stream().anyMatch(method -> method.getName().equals(parts[1])));
    }

    private static List<Class<?>> concreteClasses() {
        return new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests()).importPackages("com.suno.mall")
                .stream().filter(candidate -> !candidate.isInterface() && !candidate.isAnnotation() && !candidate.isEnum())
                .<Class<?>>map(candidate -> candidate.reflect()).collect(Collectors.toList());
    }

    private static List<Class<?>> testClasses() {
        return new ClassFileImporter().importPackages("com.suno.mall").stream()
                .filter(candidate -> !candidate.isInterface() && !candidate.isAnnotation() && !candidate.isEnum())
                .<Class<?>>map(candidate -> candidate.reflect()).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> yaml(Path path) throws Exception {
        try (InputStream stream = Files.newInputStream(path)) {
            LoaderOptions options = new LoaderOptions();
            options.setMaxAliasesForCollections(200);
            return ((List<Map<String, Object>>) new Yaml(new SafeConstructor(options)).load(stream));
        }
    }

    private static String string(Map<String, Object> entry, String key) {
        return String.valueOf(entry.get(key));
    }

    private static int integer(Map<String, Object> entry, String key) {
        return Integer.parseInt(String.valueOf(entry.get(key)));
    }

    private static List<?> list(Map<String, Object> entry, String key) {
        Object value = entry.get(key);
        return value instanceof List<?> values ? values : List.of();
    }
}
