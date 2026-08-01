# Professional README Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将根 README 重构为面向专业开发、架构评审和运维接手人员的工程入口页。

**Architecture:** README 只承担项目定位、架构边界、启动验证、安全基线和导航职责；API/curl、需求流程和运行细节继续由现有 `docs/` 文档承载。改动只涉及文档，不改变运行时行为。

**Tech Stack:** Markdown、Maven Wrapper、Shell repository/documentation verification scripts。

---

### Task 1: Map the current documentation surface

**Files:**
- Read: `README.md`
- Read: `docs/README.md`
- Read: `docs/development/configuration.md`
- Read: `docs/development/testing.md`
- Read: `docs/development/workflow.md`
- Read: `pom.xml`

- [ ] **Step 1: Record current facts**

Confirm the README uses Java 25, Spring Boot 3.5.16, eight Maven modules plus the parent reactor, `suno-bootstrap` as the executable module, H2/MySQL profiles, and the current verification commands.

- [ ] **Step 2: Identify content to retain as links**

Keep detailed API/curl examples in the repository, but replace their full README sections with links to the architecture, requirements, configuration, migration, testing, workflow, and business-flow documents.

### Task 2: Rewrite the professional README entry page

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Replace the opening with project positioning and status**

Describe Suno Mall as a C2B2C circular second-hand commerce platform implemented as a modular monolith, and add a compact navigation block for architecture, requirements, operations, testing, and development workflow.

- [ ] **Step 2: Add the business and architecture sections**

Explain the recycle-to-resale-to-fulfillment loop, list the eight Maven modules with responsibilities, and document the application/adapter, persistence, provider, and event/outbox boundaries without claiming a distributed deployment.

- [ ] **Step 3: Add reproducible startup and validation paths**

Provide exact commands for H2 startup, MySQL startup with externally injected secrets, liveness/readiness checks, package verification, repository hygiene, and documentation validation.

- [ ] **Step 4: Add configuration and production safety guidance**

List required environment variables, state that Spring Boot does not load `.env` automatically, prohibit committed/demo secrets in production, require least-privilege MySQL credentials, and distinguish Mock providers from staging/prod real providers.

- [ ] **Step 5: Add API orientation and lifecycle boundaries**

Keep a concise representative endpoint table, link to the full requirements catalog, and explicitly label Phase 0 scope, known verification limits, and next-phase requirements.

### Task 3: Verify the documentation change

**Files:**
- Test: `scripts/verify-repository.sh`
- Test: `scripts/verify-docs.sh`

- [ ] **Step 1: Run repository hygiene checks**

Run `./scripts/verify-repository.sh` and confirm it exits with code 0.

- [ ] **Step 2: Run documentation checks**

Run `./scripts/verify-docs.sh` and confirm it exits with code 0.

- [ ] **Step 3: Inspect the diff and links**

Run `git diff --check` and review the README diff for stale commands, broken relative links, unclosed code fences, and claims that conflict with `pom.xml` or the development docs.

- [ ] **Step 4: Commit the README update**

```bash
git add README.md
git commit -m "docs: professionalize project README"
```
