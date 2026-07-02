---
name: Review Code Structure
description: Audit class responsibilities, name–responsibility alignment, rule compliance, module boundaries, duplication, and reuse opportunities across Stomp4J.
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
| God-class signals | >400 lines, >8 unrelated public methods, or >5 unrelated fields without clear grouping |

**Exempt** (note as skipped, do not flag): one-field records, trivial enums, private nested types &lt;15 lines, pure DTOs with no behaviour.

For each gap, state: missing block, vague Doing, scope creep, or suggested split/merge.

### Name ↔ responsibility alignment

Read [stomp4j-oop-design.mdc](../rules/stomp4j-oop-design.mdc) §1 (exists in real life) and §6 (name is not a job title). Cross-check **every non-exempt type** — name must match what the type **is** and what its **Doing** bullets / behaviour actually own.

| Check | Pass criteria |
|-------|----------------|
| Name matches Doing | Primary noun in the class name appears in **Doing** (or is an established domain synonym from [domain-specification.md](../../docs/domain-specification.md)) |
| Real-life entity | Name answers *“What does this object represent?”* — a `Session`, `Channel`, `Transport`, `Message`, not a procedure |
| Not a job title | Avoid `*Parser`, `*Decoder`, `*Dispatcher`, `*Manager`, `*Utils`, `*Helper`, `*Processor`, `*Controller` unless the type is an **exported** handler/SPI contract (`MessageHandler`, `TransportProvider`, …) |
| Suffix conventions | `*Impl` only on public-API implementations; `*Factory` / `*Builder` only when the type constructs other objects; `*Provider` only for SPI registration |
| Layer honesty | Name reflects module and package (`TcpChannel` in `server.channels`, `TcpTransport` in `client` transport) — no `Client*` in server or vice versa |
| Singular identity | Lifecycle types are singular (`Session`, `Subscription`); collections/buffers use plural or collective nouns (`MessageBuffer`, `Headers`) |

**How to verify each type**

1. Read the class name and package.
2. Read the responsibility block **Doing** (or infer from public methods if block is missing).
3. Ask: *Would a new contributor expect this name given only the Doing list?*
4. Flag **mismatches** with evidence:

| Mismatch kind | Example | Severity |
|---------------|---------|----------|
| Name understates scope | `MessageBuffer` also negotiates protocol version | major |
| Name overstates scope | `ConnectionManager` only closes a socket | major |
| Job title, not entity | `FrameDecoder` only accumulates bytes until NUL | minor–major |
| Wrong layer noun | `StompClient` logic in a server `*Handler` class | major |
| Established name kept | `TopicConsumerManager` — note if Doing matches domain or flag for rename | case-by-case |

**Established exceptions** (do not flag without evidence of mismatch): exported `*Handler`, `*Authenticator`, `TransportProvider`, `Stomp` protocol types, Spring/Quarkus `*Lifecycle`, test `*Fixture` / `*Support`.

Mechanical scan — review every hit; many are valid contracts:

```bash
# Job-title suffix candidates (main sources)
rg -n '\b(class|interface|record|enum) \w+(Handler|Manager|Utils|Helper|Processor|Controller|Parser|Decoder|Dispatcher|Reader|Writer|Listener)\b' \
  --glob '**/src/main/**/*.java' <scope-paths>

# Factory/Builder/Provider without construction role — spot-check
rg -n '\b(class|interface) \w+(Factory|Builder|Provider)\b' --glob '**/src/main/**/*.java' <scope-paths>
```

For each mismatch: cite **current name**, **what Doing actually says**, **suggested rename** (or merge target), and whether rename is **breaking** (public API) vs internal-only.

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

```

Flag only **actionable** violations in scope; group repeated patterns once with a count. Name ↔ responsibility findings belong in **§1** of the report (not duplicated here unless also a raw rule violation).

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
3. **P2** — Name ↔ responsibility mismatches on public API, missing responsibility blocks, Demeter / Tell-Don't-Ask smells
4. **P3** — Internal rename candidates, minor duplication, documentation gaps

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

### Name ↔ responsibility mismatches

| Severity | Type | Name issue | Doing / behaviour | Suggested rename or merge |
|----------|------|------------|-------------------|---------------------------|
| … | … | … | … | … |

### Names consistent with responsibilities

{brief count or list of well-aligned exemplars, e.g. `TcpOutboundQueue`, `MessageBuffer`}

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

- Prefer **parallel** `explore` subagents per module for Phases 2–5, then merge into one report. Phase 2 must include the **name ↔ responsibility** pass for every inventoried type.
- Sample first, deep-dive on **channels**, **session**, **protocol**, **transport**, and **bridge** packages — highest churn.
- If scope is huge, complete one module fully before skimming the rest; say so in the executive summary.
- Finish with: `✅ Code structure review written to reports/…` and the report path.

Start the audit now.
