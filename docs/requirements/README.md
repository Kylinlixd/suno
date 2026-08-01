# Requirements contract

`use-cases.yaml` is the authoritative, machine-readable inventory for every public HTTP route, scheduled action, and cross-module event. Its companion schema defines the contract; `public-events.yaml` is the versioned registry for `EVENT` entries.

Actors are `anonymous`, `customer`, `administrator`, `payment-gateway`, `scheduler`, and `system`. Shared state changes are expressed by each use case’s invariants, while errors are stable application error codes or delivery failures rather than transport-specific prose. List endpoints use zero-based `page` and bounded `size`; responses retain the existing `ApiResponse` envelope until a documented compatibility migration replaces it.

Each entry has one documentation task (9–13), exact requirement/current-development anchors, real current Java symbols, and either executable `implementedTests` or phase-scoped `plannedTests`. A partial or absent implementation must state its target architecture flow and explicit gaps in its requirement document.

## Adding a route, scheduler, or event

1. Allocate a stable, non-reused ID in the owner’s decision group and add the catalog entry before implementation.
2. Add the route or scheduler exactly once; preserve the HTTP method/path or scheduled method/property identity.
3. Add anchors, non-empty Mermaid requirement and current-development flows, test mappings, and target/gap sections when the implementation is not complete.
4. For an event, allocate a `-E` ID only (never a duplicate HTTP ID), add the same id/type/version/owner record to `public-events.yaml`, and implement the public event boundary when its target phase arrives.
5. Run `./scripts/verify-requirement-flows.sh --task N` for the owning task and `./scripts/verify-docs.sh` before review.

Compatibility is forward-only: IDs, public paths, error codes, and registry versions are retained. Breaking behavior requires a new documented contract and an explicit migration/deprecation period.
