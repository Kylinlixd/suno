package com.suno.mall.documentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.suno.mall.core.event.DocumentedDomainEvent;
import com.suno.mall.core.event.DomainEvent;
import com.suno.mall.core.event.EventVersion;
import com.suno.mall.core.event.UseCaseId;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

class DocumentationFlowCoverageTest {

    private static final Path CATALOG = Path.of("..", "docs", "requirements", "use-cases.yaml");
    private static final Path EVENTS = Path.of("..", "docs", "requirements", "public-events.yaml");
    private static final Pattern HEADING = Pattern.compile("(?m)^(#{2,})\\s+%s(?:\\s|$).*?$");
    private static final Pattern PLANNED_TEST = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$.]*#[A-Za-z_$][A-Za-z0-9_$]*");
    private static final Pattern API_EVENT_PACKAGE = Pattern.compile(
            "com\\.suno\\.mall\\.(identity|recycle|marketplace|payment|operations)\\.api\\.event(\\..*)?");
    private static final List<Class<?>> CONCRETE_CLASSES = importConcreteClasses(new ImportOption.DoNotIncludeTests());
    private static final List<Class<?>> TEST_CLASSES = importConcreteClasses();

    @Test
    void catalogEntriesHaveCompleteHonestFlowsAndTestContracts() throws Exception {
        List<Map<String, Object>> catalog = yaml(CATALOG);
        assertEquals(122, catalog.size());
        for (Map<String, Object> entry : catalog) {
            String id = string(entry, "id");
            String document = Files.readString(Path.of("..", string(entry, "requirementDoc")));
            String section = sectionFor(document, id);
            assertNotNull(section, () -> id + " is missing its requirement section");
            assertFlow(section, string(entry, "requirementAnchor"), id, "requirement");
            assertFlow(section, string(entry, "developmentAnchor"), id, "current development");
            list(entry, "currentSymbols").forEach(symbol -> assertTrue(symbolExists(String.valueOf(symbol)),
                    () -> id + " unresolved current symbol " + symbol));
            if (!"implemented".equals(string(entry, "implementationStatus"))) {
                assertTrue(section.matches("(?sm).*^#{2,6}\\s+Target architecture flow\\s*$.*"),
                        () -> id + " must have a target architecture flow");
                assertTrue(section.matches("(?sm).*^#{2,6}\\s+Gaps\\s*$.*"),
                        () -> id + " must have explicit gaps");
                assertTrue(section.contains("targetPhase: " + string(entry, "targetPhase")),
                        () -> id + " gaps must state its target phase");
            }
            list(entry, "implementedTests").forEach(test -> assertTrue(testExists(String.valueOf(test)),
                    () -> id + " unresolved implemented test " + test));
            for (Object planned : list(entry, "plannedTests")) {
                assertTrue(planned instanceof Map<?, ?>, () -> id + " planned test must be a mapping");
                Map<?, ?> plannedTest = (Map<?, ?>) planned;
                assertTrue(PLANNED_TEST.matcher(String.valueOf(plannedTest.get("test"))).matches(),
                        () -> id + " planned test must use Class#method notation");
                assertEquals(integer(entry, "targetPhase"), Integer.parseInt(String.valueOf(plannedTest.get("targetPhase"))),
                        () -> id + " planned test must declare the catalog target phase");
            }
            assertFalse(list(entry, "implementedTests").isEmpty() && list(entry, "plannedTests").isEmpty(),
                    () -> id + " must own an implemented or planned test");
        }
    }

    @Test
    void independentlyDiscoveredEventsMatchTheRegistryAndCatalogInBothDirections() throws Exception {
        List<Map<String, Object>> catalog = yaml(CATALOG);
        List<Map<String, Object>> registry = yaml(EVENTS);
        Map<String, Map<String, Object>> catalogEvents = catalog.stream().filter(entry -> "EVENT".equals(string(entry, "kind")))
                .collect(Collectors.toMap(entry -> string(entry, "id"), Function.identity()));
        Map<String, Map<String, Object>> registryEvents = registry.stream()
                .collect(Collectors.toMap(entry -> string(entry, "id"), Function.identity()));
        assertEquals(catalogEvents.keySet(), registryEvents.keySet());

        Set<Class<?>> discovered = concreteClasses().stream().filter(DocumentationFlowCoverageTest::isPublicEvent)
                .collect(Collectors.toSet());
        for (Class<?> eventType : discovered) {
            assertTrue(DocumentedDomainEvent.class.isAssignableFrom(eventType),
                    () -> eventType.getName() + " must implement DocumentedDomainEvent");
            assertTrue(eventType.isAnnotationPresent(UseCaseId.class),
                    () -> eventType.getName() + " must carry @UseCaseId");
            assertTrue(eventType.isAnnotationPresent(EventVersion.class),
                    () -> eventType.getName() + " must carry @EventVersion");
            String id = eventType.getAnnotation(UseCaseId.class).value();
            Map<String, Object> registered = registryEvents.get(id);
            Map<String, Object> catalogued = catalogEvents.get(id);
            assertNotNull(registered, () -> eventType.getName() + " is absent from public-events.yaml");
            assertNotNull(catalogued, () -> eventType.getName() + " is absent from use-cases.yaml");
            assertEquals(eventType.getSimpleName(), string(registered, "eventType"));
            assertEquals(eventType.getAnnotation(EventVersion.class).value(), integer(registered, "version"));
            assertEquals(ownerFor(eventType), string(registered, "owner"));
            assertEquals(eventKey(registered), eventKey(catalogued));
        }
        for (Map<String, Object> event : catalogEvents.values()) {
            if ("implemented".equals(string(event, "implementationStatus"))) {
                assertEquals(1, discovered.stream().filter(type -> type.getAnnotation(UseCaseId.class).value()
                        .equals(string(event, "id"))).count(), () -> string(event, "id") + " must have one concrete event type");
            }
        }
    }

    private static void assertFlow(String section, String anchor, String id, String label) {
        int index = anchorIndex(section, anchor);
        assertTrue(index >= 0, () -> id + " missing " + label + " anchor " + anchor);
        String remaining = section.substring(index);
        int opening = remaining.indexOf("```mermaid");
        assertTrue(opening >= 0, () -> id + " missing " + label + " Mermaid block");
        int bodyStart = remaining.indexOf('\n', opening);
        int closing = remaining.indexOf("```", bodyStart + 1);
        assertTrue(bodyStart >= 0 && closing >= 0 && !remaining.substring(bodyStart + 1, closing).isBlank(),
                () -> id + " has an empty " + label + " Mermaid block");
    }

    private static String sectionFor(String document, String id) {
        Matcher heading = Pattern.compile(String.format(HEADING.pattern(), Pattern.quote(id))).matcher(document);
        if (!heading.find()) {
            return null;
        }
        int level = heading.group(1).length();
        Matcher boundary = Pattern.compile("(?m)^(#{2,})\\s+").matcher(document);
        boundary.region(heading.end(), document.length());
        while (boundary.find()) {
            if (boundary.group(1).length() <= level) {
                return document.substring(heading.start(), boundary.start());
            }
        }
        return document.substring(heading.start());
    }

    private static int anchorIndex(String section, String anchor) {
        return List.of(section.indexOf("id=\"" + anchor + "\""), section.indexOf("id='" + anchor + "'"),
                section.indexOf("{#" + anchor + "}")).stream().filter(index -> index >= 0).findFirst().orElse(-1);
    }

    private static boolean symbolExists(String symbol) {
        String[] parts = symbol.split("#", 2);
        return parts.length == 2 && concreteClasses().stream().filter(type -> type.getSimpleName().equals(parts[0]))
                .anyMatch(type -> List.of(type.getDeclaredMethods()).stream().anyMatch(method -> method.getName().equals(parts[1])));
    }

    private static boolean testExists(String reference) {
        String[] parts = reference.split("#", 2);
        return parts.length == 2 && TEST_CLASSES.stream().filter(type -> type.getSimpleName().equals(parts[0]))
                .anyMatch(type -> List.of(type.getDeclaredMethods()).stream().anyMatch(method -> method.getName().equals(parts[1])));
    }

    private static List<Class<?>> concreteClasses() {
        return CONCRETE_CLASSES;
    }

    private static List<Class<?>> importConcreteClasses(ImportOption... options) {
        ClassFileImporter importer = new ClassFileImporter();
        for (ImportOption option : options) {
            importer = importer.withImportOption(option);
        }
        return importer.importPackages("com.suno.mall")
                .stream().filter(candidate -> !candidate.isInterface() && !candidate.isAnnotation() && !candidate.isEnum())
                .<Class<?>>map(candidate -> candidate.reflect()).toList();
    }

    private static boolean isPublicEvent(Class<?> type) {
        return DomainEvent.class.isAssignableFrom(type) || API_EVENT_PACKAGE.matcher(type.getPackageName()).matches();
    }

    private static String ownerFor(Class<?> type) {
        for (String segment : type.getPackageName().split("\\.")) {
            if (Set.of("identity", "recycle", "marketplace", "payment", "operations").contains(segment)) {
                return Character.toUpperCase(segment.charAt(0)) + segment.substring(1);
            }
        }
        throw new AssertionError("public event has no feature-module owner: " + type.getName());
    }

    private static String eventKey(Map<String, Object> event) {
        return String.join("|", string(event, "id"), string(event, "eventType"), String.valueOf(integer(event, "version")),
                string(event, "owner"));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> yaml(Path path) throws Exception {
        try (InputStream stream = Files.newInputStream(path)) {
            LoaderOptions options = new LoaderOptions();
            options.setMaxAliasesForCollections(200);
            return (List<Map<String, Object>>) new Yaml(new SafeConstructor(options)).load(stream);
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
