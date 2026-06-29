---
name: Fix All Tests
description: Automatically fix all failing Maven tests by iterating until they pass.
---

You are an expert Java developer. Your task is to fix **all failing tests** in this Maven project.

**Prerequisites:** Docker must be running (Testcontainers / ActiveMQ).

Follow this exact loop — **do not ask for confirmation** and **do not invent workarounds**.

1. **Run all tests**
   ```bash
   mvn verify
   ```
   Or per module: `mvn -pl commons test`, `mvn -pl client test`, `mvn -pl server test`.

2. **Check for failures**
   - If **no test failures**, print `✅ All tests pass!` and stop.
   - Otherwise proceed to step 3.

3. **List each failing test with the reason**
   Parse `*/target/surefire-reports/*.txt` or console output. For each failure:
   - Test class & method name
   - Module (`commons`, `client`, `server`)
   - Exception type and stack trace
   - Assertion details (expected vs actual)

4. **Fix each failing test** (one at a time)
   - Read `ARCHITECTURE.md` for module boundaries and test strategy.
   - Read the **test code** and **production code** it calls.
   - Determine root cause: production bug, incorrect assertion, broker/container setup, etc.
   - **Apply a direct fix** — never `Thread.sleep()`, `@Disabled`, or swallowed exceptions.
   - Re-run single test: `mvn -pl client test -Dtest=StompClientTcpTest#methodName`

5. **Repeat from step 1** until green.

**Log changes** in `reports/test_fix_log-{sequential}-{dd-MM-yyyy-HH-mm-ss}.md` (file, old/new code, reason).

Start the loop now.
