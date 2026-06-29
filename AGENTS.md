# Agent instructions (Stomp4J)

Read these before changing code or tests:

| Document | Purpose |
|----------|---------|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Modules, patterns, SPI, testing, CI, feature workflow |
| [docs/domain-specification.md](docs/domain-specification.md) | STOMP ubiquitous language and invariants |
| [README.md](README.md) | Client usage examples |
| [resources/roteiros/](resources/roteiros/) | Design rationale (Portuguese) |
| [.cursor/rules/](.cursor/rules/) | Cursor rules (always-on + file-scoped) |

**Workflow:** read domain spec → place code in correct module → update SPI/`module-info` if needed → test → update ARCHITECTURE.md when architecture changes → `mvn verify`.

**Tests:** integration tests use Testcontainers (Docker required). Reuse `StompContainer` and existing test infra; do not bypass with raw sockets when the DSL exists.

**Rules index:**

| Rule | Scope |
|------|-------|
| `architecture.mdc` | Always read/update ARCHITECTURE.md |
| `stomp4j-core.mdc` | Core conventions |
| `stomp4j-module-architecture.mdc` | Module layers and dependencies |
| `stomp4j-module-boundaries.mdc` | JPMS export/import rules |
| `stomp4j-java.mdc` | Java style (`**/*.java`) |
| `stomp4j-format-imports.mdc` | Import hygiene |
| `stomp4j-strings.mdc` | String building |
| `stomp4j-tell-dont-ask.mdc` | Intent methods |
| `stomp4j-law-of-demeter.mdc` | No train wrecks |
| `stomp4j-no-method-bypass-allowed.mdc` | No pass-through wrappers |
| `stomp4j-tests.mdc` | Test conventions |
| `stomp4j-test-docker.mdc` | Docker for Testcontainers |
| `development-experience.mdc` | Local broker and test infra |
| `domain-model.mdc` | Domain language alignment |
| `stomp4j-tooling-languages.mdc` | Scripts: bash/JBang only |
| `static-analysis.mdc` | Finish gate: `mvn verify` |
