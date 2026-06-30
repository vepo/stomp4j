---
name: Fix All Tests
description: Automatically fix all failing Maven tests by iterating until they pass.
---

You are an expert Java developer. Your task is to fix **all failing tests** in this Maven project.

**Prerequisites:** Docker must be running (Testcontainers / ActiveMQ).

Follow [stomp4j-test-during-development.mdc](../rules/stomp4j-test-during-development.mdc) and [stomp4j-test-failure-diagnosis.mdc](../rules/stomp4j-test-failure-diagnosis.mdc).

Follow this exact loop — **do not ask for confirmation** and **do not invent workarounds**.

1. **Discover failures (scoped first)**
   ```bash
   mvn -Pfast -pl commons,client,server test
   ```
   If integration tests may be affected, run the relevant module without `-Pfast`:
   ```bash
   mvn -pl client test
   mvn -pl server test
   ```
   Only run full `mvn verify` after module tests are green or when failures span multiple modules.

2. **Check for failures**
   - If **no test failures** in affected modules, run `mvn verify` once. If green, print `✅ All tests pass!` and stop.
   - Otherwise proceed to step 3.

3. **List each failing test with the reason**
   Parse `*/target/surefire-reports/*.txt` or console output. **Group failures by root cause** (same port, same broker setup, same race → one fix). For each failure:
   - Test class & method name
   - Module (`commons`, `client`, `server`, …)
   - Exception type and stack trace
   - Assertion details (expected vs actual)

4. **Fix each root cause** (one at a time)
   - Read `ARCHITECTURE.md` for module boundaries and test strategy.
   - Read the **test code** and **production code** it calls.
   - Determine root cause: production bug, incorrect assertion, broker/container setup, etc.
   - **Apply a direct fix** — never `Thread.sleep()`, `@Disabled`, or swallowed exceptions.
   - Re-run single test: `mvn -pl client test -Dtest=StompClientTcpTest#methodName`
   - Then re-run the module: `mvn -pl client test`

5. **Repeat steps 3–4** until affected module tests pass, then run **`mvn verify` once**.

**After fix is green:** write one report under `${mavenRoot}/reports/` using the template in [stomp4j-test-failure-diagnosis.mdc](../rules/stomp4j-test-failure-diagnosis.mdc) §6 (`{sequential}-test-failure-…` filename).

Start the loop now.
