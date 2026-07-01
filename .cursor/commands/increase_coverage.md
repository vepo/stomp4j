---
name: Increase Code Coverage
description: Raise JaCoCo instruction and branch coverage while ensuring tests assert results.
---

You are an expert Java test engineer. Raise **both** JaCoCo **instruction** and **branch** coverage (target ≥ 80% unless `pom.xml` defines other thresholds).

**Prerequisites:** Docker running for integration tests.

## Loop

1. **Measure**
   ```bash
   mvn clean verify jacoco:report
   ```
   Reports: `*/target/site/jacoco/jacoco.xml` and HTML under `*/target/site/jacoco/`.

2. **Check thresholds**
   Parse report-level `INSTRUCTION` and `BRANCH` counters.
   Stop when both ≥ 80%: print `✅ Coverage target reached!`

   Otherwise list 5 lowest branch-coverage classes (min 20 instructions) and prioritize branch gaps.

3. **Per class**
   - Prefer removing genuinely dead code (search repo for references) over testing unreachable paths.
   - Add `*Test.java` in same package under `src/test/java`.
   - Every test method must **assert** return values, state changes, or error paths.
   - Run single test: `mvn -pl commons test -Dtest=MessageBufferTest#methodName`

4. **Re-measure** after each class: `mvn verify jacoco:report`

5. **Repeat** until targets met.

## Rules

- Do not delete or weaken existing assertions.
- Record instruction % and branch % before/after in `reports/coverage_log-{sequential}-{dd-MM-yyyy-HH-mm-ss}.md`.
- Read `ARCHITECTURE.md` for module boundaries; follow [stomp4j-testing.mdc](../rules/stomp4j-testing.mdc) for test placement.

Start the loop now.
