# Agent instructions (Stomp4J)

Read these before changing code or tests:

| Document | Purpose |
|----------|---------|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Modules, patterns, SPI, testing, CI, feature workflow |
| [docs/domain-specification.md](docs/domain-specification.md) | STOMP ubiquitous language and invariants |
| [docs/features.md](docs/features.md) | Supported capabilities checklist |
| [docs/README.md](docs/README.md) | User documentation index |
| [.cursor/rules/](.cursor/rules/) | Four pillars + file-scoped detail |
| [.cursor/agents/](.cursor/agents/) | Project subagents (specialized behaviour) |

**Workflow:** domain spec → correct module → create tests (TDD) → tiered test runs → `mvn verify` once → update docs when public API changes.

## Agents vs commands

| Surface | Location | Purpose |
|---------|----------|---------|
| **Subagents** | `.cursor/agents/*.md` | Specialized system prompts — TDD (development), domain modeling, protocol review. Delegate by name or let Cursor route from `description`. |
| **Commands** | `.cursor/commands/*.md` | Repeatable workflows you slash-invoke — fix all tests, Sonar loop, coverage loop. |

## Rules — four pillars (always on)

| Pillar | Rule | Covers |
|--------|------|--------|
| 1. Building the model | [stomp4j-model.mdc](.cursor/rules/stomp4j-model.mdc) | Domain language, architecture, modules, JPMS placement, **TDD development guidance**, doc triggers |
| 2. Testing | [stomp4j-testing.mdc](.cursor/rules/stomp4j-testing.mdc) | Tiered Maven commands, impact map, failure workflow |
| 3. Coding quality | [stomp4j-quality.mdc](.cursor/rules/stomp4j-quality.mdc) | Finish gate, ReadLints, `mvn verify`, standards index |
| 4. Platform usage | [stomp4j-platform.mdc](.cursor/rules/stomp4j-platform.mdc) | Java 21, Maven, JPMS, approved libraries, tooling boundaries |

No content is duplicated across pillars — each hub links to file-scoped rules for detail.

## File-scoped rules

| Rule | Globs | Topic |
|------|-------|-------|
| [stomp4j-protocol.mdc](.cursor/rules/stomp4j-protocol.mdc) | `commons/**`, `client/**`, `server/**`, `bridge/**` | STOMP spec compliance |
| [stomp4j-oop-design.mdc](.cursor/rules/stomp4j-oop-design.mdc) | `**/*.java` | Tell/Don't Ask, Demeter, no bypass |
| [stomp4j-java.mdc](.cursor/rules/stomp4j-java.mdc) | `**/*.java` | Style, logging, `var`, streams, JPMS |
| [stomp4j-format-imports.mdc](.cursor/rules/stomp4j-format-imports.mdc) | `**/*.java` | Imports and formatting |
| [stomp4j-strings.mdc](.cursor/rules/stomp4j-strings.mdc) | `**/*.java` | String building |
| [stomp4j-in-code-documentation.mdc](.cursor/rules/stomp4j-in-code-documentation.mdc) | `**/*.java` | Non-obvious comments |
| [stomp4j-tests.mdc](.cursor/rules/stomp4j-tests.mdc) | test + core module paths | JUnit, Testcontainers, cookbooks |
| [stomp4j-test-failure-diagnosis.mdc](.cursor/rules/stomp4j-test-failure-diagnosis.mdc) | `src/test/**`, surefire reports | Failure classification and reports |
| [documentation.mdc](.cursor/rules/documentation.mdc) | `docs/**`, README | User-facing docs maintenance |
| [stomp4j-kafka-bridge.mdc](.cursor/rules/stomp4j-kafka-bridge.mdc) | `bridge/**` | Kafka bridge sync requirements |

## Project subagents (`.cursor/agents/`)

| Subagent | When to delegate |
|----------|------------------|
| [tdd-red](.cursor/agents/tdd-red.md) | New behaviour — **create** a failing test only (no production code) |
| [tdd-green](.cursor/agents/tdd-green.md) | After Red — minimal production code to pass the test |
| [tdd-refactor](.cursor/agents/tdd-refactor.md) | After Green — design cleanup, tests stay green |
| [domain-model](.cursor/agents/domain-model.md) | Before coding — domain-spec and vocabulary |
| [protocol-compliance](.cursor/agents/protocol-compliance.md) | Before merge — STOMP spec review |
| [bridge-integration](.cursor/agents/bridge-integration.md) | Kafka bridge mapping, config, tests |
| [docs-sync](.cursor/agents/docs-sync.md) | After API/behaviour change — user docs |

**TDD cycle (development):** `tdd-red` (create test) → `tdd-green` → `tdd-refactor` unless the user stops after Green.

Example: *"Use tdd-red to create a test for …"*

## Built-in Task subagents

| Subagent | When to use |
|----------|-------------|
| `explore` | Map codebase before a large feature |
| `shell` | Maven/Docker loops, git, CI reproduction |
| `ci-investigator` | Single failing PR check |
| `bugbot` | User-requested diff review |
| `security-review` | User-requested security review |
| `generalPurpose` | Multi-step work outside specialists |

## Commands (workflows only)

| Command | Purpose |
|---------|---------|
| [fix_tests.md](.cursor/commands/fix_tests.md) | Loop until tests pass |
| [fix_sonar_issues.md](.cursor/commands/fix_sonar_issues.md) | Static analysis fixes |
| [increase_coverage.md](.cursor/commands/increase_coverage.md) | Coverage improvements |
