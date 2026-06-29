---
name: Fix Sonar Issues
description: Run local static analysis and fix findings with conservative, behavior-preserving changes.
---

You are an expert Java developer working on Stomp4J. Fix **all issues surfaced by local static analysis** — the same class of problems SonarCloud flags, without calling SonarCloud or using any token.

Align with [static-analysis.mdc](../../.cursor/rules/static-analysis.mdc): **local checks only**.

Follow this loop — **do not ask for confirmation** before editing; **stop and report** if a fix would change observable behavior and the correct behavior is unclear.

## 1. Discover issues (local)

```bash
mvn -B compile -Dmaven.compiler.showWarnings=true -Dmaven.compiler.showDeprecation=true
```

Scan IDE diagnostics: `ReadLints` on changed `src/main/java` and `src/test/java`.

Do **not** call SonarCloud API. Do **not** ask for `SONAR_TOKEN`.

## 2. Prioritize

1. Compile errors, failing `verify`
2. Reliability smells: empty catches, `System.out`, `printStackTrace`
3. Compiler + linter warnings: unused code, deprecations
4. Maintainability: cognitive complexity, duplicated literals
5. Localized fixes before cross-cutting refactors

## 3. Before touching code

1. Read file and sibling patterns ([stomp4j-java.mdc](../../.cursor/rules/stomp4j-java.mdc), [ARCHITECTURE.md](../../ARCHITECTURE.md)).
2. Protocol changes → [docs/domain-specification.md](../../docs/domain-specification.md).
3. Smallest fix addressing root cause.

## 4. Fix strategies

| Theme | Safe approach | Avoid |
|-------|---------------|-------|
| Unused imports / dead code | Remove after confirming zero references | Deleting SPI or exported API |
| Exception handling | SLF4J + rethrow or domain exception | Empty catch |
| Test smells | Fix assertion or setup | `@Disabled`, weakened assertions |
| JPMS | Update `module-info` with intentional exports | Breaking consumers |

**Never:** `//NOSONAR` without fix; weaken protocol handling; delete tests to green CI.

## 5. Verify each batch

```bash
mvn -B compile -Dmaven.compiler.showWarnings=true
mvn -B verify
```

Docker required for integration tests.

## 6. Log every change

Append to `reports/sonar_fix_log-{sequential}-{dd-MM-yyyy-HH-mm-ss}.md`.

## 7. Stop when

- `mvn verify` passes
- `ReadLints` clean on touched files

Print `✅ Local static analysis clean!` and summarize.

## 8. If stuck

Report finding, why fix is unclear, two options. Do not apply risky workarounds.

Start the loop now.
