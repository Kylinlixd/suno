# Phase 0 Pre-flight Corrections Report

## Status

PASS — the approved design and Phase 0 plan were corrected before Task 2. This was a documentation-only gate; no production code was changed and nothing was pushed.

## Correction commit

`b8589e576a27c5047f62839d23dba48b55207884` — `docs: correct phase zero execution plan`

## Changed sections

- Design spec: 7.1 schema authority, 7.2 migration ranges, 12.2 per-use-case documentation, 12.3 machine validation, and completion criterion 15.9.
- Phase 0 plan: Tasks 3, 5, 6, 8–15. The original 15-task structure and unrelated architecture decisions remain intact.

## Eight required corrections and evidence

1. **Flyway dependencies/database support:** Task 5 now modifies root `pom.xml` and `suno-bootstrap/pom.xml`; it explicitly lists managed, unversioned `flyway-core`, `flyway-mysql`, H2 runtime, and test-scoped Spring Boot Test/Testcontainers dependencies, with Spring Boot 3.5.16 as version authority.
2. **Legacy compatibility:** the design and Task 5 specify `V0001__Normalize_legacy_table_names.java`, `DatabaseMetaData`, the fixed 21-table allowlist, H2/MySQL dialect-aware in-place rename, populated collision abort, no silent merge/overwrite, compatible V1000–V5000 completion migrations, fixture-backed H2/MySQL preservation checks, and collision failure coverage.
3. **Dev seed isolation:** common/mysql/staging/prod locations contain only `classpath:db/migration`; only `application-dev.yml` adds `classpath:db/dev`. `R__dev_seed.sql` is idempotent and `FlywayLocationProfileTest` proves non-dev isolation.
4. **Truthful current/target docs:** the catalog requires `implementationStatus`, `currentSymbols`, and `targetPhase`. Every item has a real-symbol Phase 0 current flow; target flows and explicit gaps are required only when current differs. `CurrentActor` and `PaymentEventProcessor` are forbidden from current-flow blocks until implemented.
5. **Executable versus planned tests:** `tests` was replaced by `implementedTests` and `plannedTests`; implemented mappings resolve strictly, planned entries carry exact `Class#method` plus `targetPhase`, and every use case requires at least one list with planned mappings plainly labeled.
6. **Exact ownership:** Task 8 contains 93 unique HTTP ID/method/path rows and six unique scheduler rows. It assigns `PAY-002`, leaves `MKT-025` unused, assigns publish to `MKT-100`, represents Recycle only with `REC-E005`, and corrects exact Tasks 9–13 IDs and counts.
7. **Actuator/readiness:** Task 14 adds the Actuator dependency, exposure configuration, `FlywayReadinessHealthIndicator`, ordered security rules, and `HealthEndpointIT`. Only liveness/readiness are public; other Actuator endpoints require an administrator, and readiness verifies database plus Flyway validation.
8. **No committed red tests:** the smoke test moved from Task 3 to Task 5 after Flyway/minimal startup configuration. Task 3 runs all surviving focused unit tests. Task 8 adds a passing `DocumentationCatalogCoverageTest`; Task 13 adds `DocumentationFlowCoverageTest` only after all requirement files exist. Task 1 remains the sole intentional red-baseline task.

## Validation commands and output

- `git diff --check` → exit 0, no output.
- legacy contradiction `rg` scan over both changed documents → `Legacy contradiction scan: clean`.
- structure/count check → `Structure counts: tasks=15 HTTP=93/93 schedulers=6/6`.
- ownership duplicate check → `Ownership uniqueness: 93 unique IDs and method/path pairs`.
- Markdown fence check → design: 16 triple fences/0 quadruple; plan: 28 triple fences/2 quadruple; both balanced.
- `git diff --name-only` before commit → only the approved design spec and Phase 0 plan.
- commit result → 2 files changed, 218 insertions, 65 deletions.

## Self-review

- Re-read the eight brief corrections against the final diff.
- Confirmed exactly 15 Task headings remain.
- Compared ownership-table totals with source annotations: 93 HTTP mappings and six `@Scheduled` methods.
- Confirmed Task 3 has no smoke test and Task 5 owns it.
- Confirmed old class name `DocumentationCoverageTest`, ambiguous YAML `tests: [...]`, old copy-data migration wording, approximate admin ranges, and the old red Task 8 verification are absent.
- Confirmed Markdown fences are balanced and no production-code path changed.

## Concerns

None. Docker-backed MySQL execution is intentionally an implementation-time requirement in Tasks 5–6; this pre-flight gate changed documentation only.
