---
name: Review Code Structure
description: Audit class responsibilities, rule compliance, module boundaries, duplication, and reuse opportunities across Stomp4J.
---

You are a senior Java architect reviewing the Stomp4J codebase. Produce a **read-only structural audit** — do **not** change production or test code unless the user explicitly asks to fix findings afterward.

**Prerequisites:** Read [ARCHITECTURE.md](../../ARCHITECTURE.md) §2–§3 (modules), §12 (JPMS), and [docs/domain-specification.md](../../docs/domain-specification.md) before auditing.

## Scope

Default: **full repository** (`commons`, `client`, `server`, `bridge/`, `spring/`, `quarkus/`).

If the user names a module or path (e.g. `server`, `client/internal`, `bridge/stomp4j-kafka-bridge`), restrict the audit to that scope but still check **cross-module** imports and duplication against neighbours.

## Output

Write one report:

`reports/code-structure-review-{sequential}-{dd-MM-yyyy-HH-mm-ss}.md`

Use the report template in **§ Report template** below. Every finding must cite **file path** and, when useful, **line range**. Severity: `critical` | `major` | `minor` | `suggestion`.

**Do not ask for confirmation** before starting the audit. **Do not** apply refactors in this command — only document findings and prioritized recommendations.

---

## Phase 1 — Inventory

1. List modules from the parent `pom.xml` and `ARCHITECTURE.md` §2.
2. For each module in scope, list:
   - `module-info.java` exports / requires / provides
   - Public packages (`src/main/java/dev/vepo/stomp4j/...`)
   - Internal packages (`internal`, `channels`, `session`, `bridge/internal`, etc.)
3. Build a **type inventory**: every `class`, `interface`, `record`, `enum` in scope (exclude generated or trivial one-liners only when obviously exempt).

Use `explore` or targeted `Grep` / `Glob` — do not load every file blindly; sample by package then deep-dive hotspots.

---

## Phase 2 — Class responsibilities

Read [.cursor/rules/stomp4j-class-responsibilities.mdc](../rules/stomp4j-class-responsibilities.mdc).

For each **non-exempt** type in scope:

| Check | Pass criteria |
|-------|----------------|
| Responsibility block | Javadoc with `<b>Responsibilities</b>`, at least **Doing**, and **Not responsible for** naming a neighbour |
| Single secret | One design decision per type (Parnas) — no TLS + subscription routing in one class |
| Domain naming | Nouns from domain spec (`Session`, `Transport`, `Message`) — not job titles (`*Handler`, `*Manager`, `*Utils`) unless established |
| God-class signals | >400 lines, >8 unrelated public methods, or >5 unrelated fields without clear grouping |

**Exempt** (note as skipped, do not flag): one-field records, trivial enums, private nested types &lt;15 lines, pure DTOs with no behaviour.

For each gap, state: missing block, vague Doing, scope creep, or suggested split/merge.

---

## Phase 3 — Rules compliance

Audit each type against project rules. Read the rule files — do not rely on memory.

| Rule file | What to verify |
|-----------|----------------|
| [stomp4j-oop-design.mdc](../rules/stomp4j-oop-design.mdc) | Information hiding, Tell/Don't Ask, Demeter (no train wrecks), `final`/`abstract`, decoration over override, no representation leak (`ByteBuffer`, `SSLEngine`, `SelectionKey` in public API) |
| [stomp4j-java.mdc](../rules/stomp4j-java.mdc) | SLF4J only (no `System.out` / JUL), `var` where idiomatic, threading model matches ARCHITECTURE §4 |
| [stomp4j-format-imports.mdc](../rules/stomp4j-format-imports.mdc) | Import order, no unused imports, no unnecessary FQN |
| [stomp4j-strings.mdc](../rules/stomp4j-strings.mdc) | String building patterns |
| [stomp4j-exceptions.mdc](../rules/stomp4j-exceptions.mdc) | Domain exceptions, ERROR logging, `InterruptedException` / close cleanup |
| [stomp4j-in-code-documentation.mdc](../rules/stomp4j-in-code-documentation.mdc) | Non-obvious logic documented at method/block level (not duplicating class block) |
| [stomp4j-protocol.mdc](../rules/stomp4j-protocol.mdc) | STOMP framing/command handling in correct layer (`commons` vs client/server) |
| [stomp4j-platform.mdc](../rules/stomp4j-platform.mdc) | No Spring/Quarkus/Kafka in core `commons`/`client`/`server` production code |
| [stomp4j-tests.mdc](../rules/stomp4j-tests.mdc) | Test naming narrative, meaningful assertions, no `Thread.sleep()` for sync |

Run a quick mechanical scan where helpful:

```bash
# Logging / stdout smells
rg -n 'System\.(out|err)\.|java\.util\.logging' --glob '*.java' <scope-paths>

# Cross-layer imports (examples — extend per module)
rg -n 'import dev\.vepo\.stomp4j\.(client\.internal|server\.(channels|session))' --glob '*.java'

# Job-title class names
rg -n 'class \w+(Handler|Manager|Utils|Helper|Processor|Controller)\b' --glob '**/src/main/**/*.java' <scope-paths>
```

Flag only **actionable** violations in scope; group repeated patterns once with a count.

---

## Phase 4 — Module boundaries

Read `ARCHITECTURE.md` §2–§3 and every `module-info.java` in scope.

| Check | Pass criteria |
|-------|----------------|
| Dependency direction | `commons` ← `client` / `server`; no `commons` → client/server; production `server` does not depend on `client` |
| JPMS exports | Only intentional public API; `internal` / `channels` / `session` not exported (except test `exports … to`) |
| Package placement | Wire format in `commons`; transport in client; channels/session in server; Kafka only in `bridge/` |
| Layer leakage | No `client.internal` or server internal imports from outside owning module |
| SPI | Implementations registered in `module-info` `provides` **and** `META-INF/services/…` |
| Framework isolation | Spring / Quarkus only in their modules |

Draw or include a **mermaid** diagram of actual `requires` / illegal imports found vs intended graph from ARCHITECTURE.

---

## Phase 5 — Duplicated code

Hunt for copy-paste and parallel implementations:

1. **Structural similarity** — same method sequences, identical constants, mirrored try/catch blocks across classes (e.g. `TcpChannel` vs `WebSocketChannel`, protocol v1_0/v1_1/v1_2, client vs server framing).
2. **Mechanical tools** (run from repo root; adjust paths for scope):

```bash
# Large repeated literals (candidates for shared constant or domain type)
rg -n '".{20,}"' --glob '**/src/main/**/*.java' <scope-paths> | sort | uniq -d -f 1 | head -40

# Similar file names across modules
find <scope-paths> -name '*.java' -path '*/src/main/*' | xargs -I{} basename {} | sort | uniq -d
```

3. **Semantic duplication** — same behaviour implemented twice (heartbeat, SSL handshake steps, subscribe option mapping, error frame construction). Compare siblings side by side.

For each duplicate cluster: list files, approximate duplicated lines, and whether extraction is **low / medium / high** risk.

---

## Phase 6 — Reuse via contracts (interfaces & abstract classes)

Read [stomp4j-oop-design.mdc](../rules/stomp4j-oop-design.mdc) §2 (works by contracts) and §7 (`final` vs `abstract`).

Identify:

| Opportunity | When to recommend |
|-------------|-------------------|
| **New interface** | Two+ types expose the same public capability to consumers; tests would benefit from mocking |
| **Existing interface underused** | Callers depend on concrete class where `Transport`, `OutboundChannel`, `Stomp`, handler interfaces already exist |
| **Abstract base** | Shared skeleton with explicit extension points (protocol version hooks, channel lifecycle template) — not a grab-bag of static helpers |
| **Decoration** | Cross-cutting validation, metrics, or logging — wrap contract, do not subclass concrete `final` types |

For each recommendation: name proposed type, implementors, module placement, and why inheritance is **not** preferred if `final` + decoration fits better.

---

## Phase 7 — Reuse via composition

Identify extraction candidates where a class does multiple jobs:

| Pattern | Composition target |
|---------|-------------------|
| Channel owns TLS + framing + session dispatch | Extract `TcpSslIo`, `MessageBuffer` collaborator (may already exist — flag only if still inlined) |
| Repeated executor / queue / drain loops | Dedicated small type (`TcpOutboundQueue`-style) injected |
| Mapping blocks (headers ↔ Kafka, STOMP ↔ domain) | Value mapper or strategy object |
| Test fixtures duplicating broker setup | Consolidate into `*.tests.infra` |

For each: **extract** what, **inject into** whom, expected LOC reduction, and test impact (none / unit / integration).

---

## Prioritization

Rank findings in the report:

1. **P0** — Boundary violations, wrong module deps, representation leaks in public API
2. **P1** — God classes, significant duplication in hot paths (channels, protocol, session)
3. **P2** — Missing responsibility blocks, Demeter / Tell-Don't-Ask smells
4. **P3** — Naming, minor duplication, documentation gaps

End with a **top 5 recommended actions** (smallest high-value diffs first). Do not implement unless asked.

---

## Report template

```markdown
# Code structure review — {scope}

**Date:** {iso-date}  
**Scope:** {modules/paths}  
**Types reviewed:** {count}  
**Auditor:** Cursor agent (`review_code_structure`)

## Executive summary

{3–5 bullets: overall health, biggest risks, best reuse wins}

## 1. Class responsibilities

| Severity | Type | Finding | Recommendation |
|----------|------|---------|----------------|
| … | … | … | … |

### Missing or weak responsibility blocks

{list}

### Scope creep / split candidates

{list}

## 2. Rules compliance

| Rule area | Violations | Notes |
|-----------|------------|-------|
| OOP / Demeter | … | … |
| … | … | … |

## 3. Module boundaries

### Intended vs actual

{mermaid diagram}

### Violations

| Severity | From | To | Evidence |
|----------|------|-----|----------|

## 4. Duplicated code

| Cluster | Files | ~Lines | Risk | Suggestion |
|---------|-------|--------|------|------------|

## 5. Interfaces & abstract classes

| Proposal | Types affected | Module | Rationale |
|----------|----------------|--------|-----------|

## 6. Composition extractions

| Extract | From | Into collaborator | Test impact |
|---------|------|-------------------|-------------|

## Prioritized action plan

1. …
2. …
…

## Appendix — inventory

{optional compact table: module → package → type count}
```

---

## Execution notes

- Prefer **parallel** `explore` subagents per module for Phases 2–5, then merge into one report.
- Sample first, deep-dive on **channels**, **session**, **protocol**, **transport**, and **bridge** packages — highest churn.
- If scope is huge, complete one module fully before skimming the rest; say so in the executive summary.
- Finish with: `✅ Code structure review written to reports/…` and the report path.

Start the audit now.
