# Agent instructions (Stomp4J)

Read these before changing code or tests:

| Document | Purpose |
|----------|---------|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Modules, patterns, SPI, testing, CI, feature workflow |
| [docs/README.md](docs/README.md) | User documentation index and learning paths |
| [docs/features.md](docs/features.md) | Supported capabilities checklist — keep in sync with behaviour changes |
| [docs/domain-specification.md](docs/domain-specification.md) | STOMP ubiquitous language and invariants |
| [README.md](README.md) | Client usage examples |
| [resources/roteiros/](resources/roteiros/) | Design rationale (Portuguese) |
| [.cursor/rules/](.cursor/rules/) | Cursor rules (hubs + file-scoped) |

**Workflow:** read domain spec → place code in correct module → tiered tests → `mvn verify` once → update docs when public API changes.

**Tests:** reuse `StompContainer`, `StompTestSupport`, `EmbeddedServerFixture`. See `stomp4j-testing.mdc`.

## Rules index

### Always on (3 hubs)

| Rule | Purpose |
|------|---------|
| `stomp4j-architecture.mdc` | ARCHITECTURE.md, modules, JPMS, public API, SPI, tooling |
| `stomp4j-testing.mdc` | Tiered tests, Docker, impact map, finish checklist |
| `static-analysis.mdc` | ReadLints + `mvn verify` finish gate |

### File-scoped

| Rule | Globs |
|------|-------|
| `stomp4j-protocol.mdc` | `commons/**`, `client/**`, `server/**`, `bridge/**` |
| `stomp4j-java.mdc` | `**/*.java` — style, imports, strings, logging |
| `stomp4j-oop-design.mdc` | `**/*.java` — RDD, Tell/Don't Ask, Demeter, no bypass |
| `stomp4j-in-code-documentation.mdc` | `**/*.java` — non-obvious comments |
| `stomp4j-tests.mdc` | test paths + production that affects tests |
| `stomp4j-test-failure-diagnosis.mdc` | `**/src/test/**` — failure workflow and reports |
| `documentation.mdc` | `docs/**`, `README.md`, `features.md` |
| `stomp4j-kafka-bridge.mdc` | `bridge/**`, kafka bridge docs/samples |

### Commands

| Command | Purpose |
|---------|---------|
| `fix_tests.md` | Loop until tests pass |
| `fix_sonar_issues.md` | Static analysis fixes |
| `increase_coverage.md` | Coverage improvements |
