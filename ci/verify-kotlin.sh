#!/usr/bin/env bash
# Check a generated main of anytype-kotlin: the debug app builds, and the unit
# tests of every module the fork's patches touch pass. Run from the checkout of the result.
# Needs GPR_USER and GPR_TOKEN (a token with read:packages) to download the middleware library.
set -euo pipefail
base=${FORK_BASE:-refs/fork-main/upstream}

if [ -z "${GPR_TOKEN:-}" ] || [ -z "${GPR_USER:-}" ]; then
	echo "::error::Add the FORK_TOKEN secret (classic token with repo, workflow and read:packages) to build the app."
	exit 1
fi
printf 'gpr.usr=%s\ngpr.key=%s\n' "$GPR_USER" "$GPR_TOKEN" > github.properties
printf 'amplitude.debug=""\namplitude.release=""\nsentry_dsn=""\n' > apikeys.properties

echo "::group::assembleDebug"; ./gradlew --no-daemon assembleDebug; echo "::endgroup::"

tasks=$(git diff --name-only "$base" HEAD | cut -d/ -f1 | sort -u | while read -r m; do
	f="$m/build.gradle"; [ -f "$f" ] || f="$m/build.gradle.kts"; [ -f "$f" ] || continue
	if grep -q 'com.android' "$f"; then echo ":$m:testDebugUnitTest"; else echo ":$m:test"; fi
done || true)
if [ -z "$tasks" ]; then
	echo "no modules changed by the fork's patches"
	exit 0
fi
echo "::group::unit tests (modules changed by the fork)"
echo "$tasks"
./gradlew --no-daemon $tasks
echo "::endgroup::"
