# Notification service — Maven / Spring Boot
#
# Default `build` always runs tests. Use `package` only when you intentionally
# want artifacts without re-running the suite (CI must use `verify` / `test`).

.PHONY: build test package verify clean tree

MVN ?= ./mvnw

test:
	$(MVN) test

# Quality gate used by developers and documented as the default.
build: test
	$(MVN) -q package

# Explicit opt-out of tests — not used by CI.
package:
	$(MVN) -q -DskipTests package

verify:
	$(MVN) verify

clean:
	$(MVN) clean
	rm -rf out

tree:
	@find src -type f -name '*.java' | sort
