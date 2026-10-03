# Development and release

## Build

Use JDK 21. The official Gradle plugin resolves Patcher and smali from GitHub Packages/JitPack. Existing package-read credentials may be supplied through `GITHUB_ACTOR` and `GITHUB_TOKEN` or private user Gradle properties `gpr.user` / `gpr.key`. Never put credentials in this repository. No new PAT or workflow secret is needed for GitHub Actions; the workflow uses the repository-scoped built-in GITHUB_TOKEN.

```sh
./gradlew :patches:test :patches:buildAndroid --no-daemon
```

## Optional real-APK static integration test

Keep the APK outside the checkout. Set `CHATGPT_TEST_APK` to the extracted **base.apk** from the exact bundle in provenance.md, then run the same build command. The tests exercise all four combinations of diagnostic tracing and the default-off initial browser preference. They verify helper privacy constraints and preservation of original instruction operands, control flow, registers and exception boundaries. The test verifies the base SHA256, applies the real patches, recompiles resources, rereads output DEX and asserts the two callback overrides while the third package read remains dynamic. Temporary output remains in an OS temporary directory; the test never installs, launches, signs or uploads an APK.

```sh
CHATGPT_TEST_APK=/absolute/private/path/base.apk ./gradlew :patches:test :patches:buildAndroid --rerun-tasks
```

For CI this variable is absent, so the proprietary-APK test is skipped explicitly. Synthetic tests still run. Local integration success is documented separately; CI success alone must not be represented as Android/login verification.

## Distribution

The release.yml/.releaserc pipeline is adapted from the official template. Changes: Actions pinned to verified commit IDs; Node 24; locked npm install without lifecycle scripts; explicit source-only check, tests and a clean build directory before release; independent project identity; unused automatic PR workflow and example extension removed. Both GPL notices are bundled under licenses/ (the upstream plugin excludes META-INF notices).

The template changelog dependency is pinned to its existing lockfile commit. Gradle wrapper verifies the distribution SHA256. Dependencies are ordinary build tools; no custom network/auth endpoint or account secret is added.

Develop on `dev` and use conventional commits. `feat:`/`fix:` create **prereleases** on dev; `chore:` does not. The initial main branch is a source-only bootstrap. Do not merge experimental changes to main until device results justify a stable release. Do not upload releases manually or hand-edit generated patches-bundle.json, patches-list.json, CHANGELOG.md. Semantic release generates .mpp and matching metadata in the official format.

Before any push, run `python3 scripts/check_source_only.py` after staging. It rejects APK/APKM/DEX, compiled patch files, decompiled source markers, raw logs and common secret formats. The official Gradle wrapper JAR is the sole permitted infrastructure binary in source history. Release assets contain only the project patch bundle, never a full app.

## Future app versions

Do not relax version guards from a version-name match alone. Verify the new binary hash, signer, versionCode, request constructor and final manifest. Recheck all callback paths, PKCE/state behavior and provider/permission references. Extend the exact fingerprint and real-APK test only from evidence. Never spoof Integrity verdicts or discard authentication checks to make a test appear successful.

## Release dependency audit (2026-10-03)

Locked dependencies were installed with lifecycle scripts disabled. The new registry audit reports **36 affected package entries (35 high, one moderate, zero critical)** from 12 distinct advisories. Compatible-only `npm audit fix --ignore-scripts` left dependency versions and the findings unchanged; no forced downgrade or incompatible dependency replacement was applied. The audited release engine is semantic-release 25.0.9, with micromatch 4.0.8 / braces 3.0.3 and bundled npm 11.21.0. Registry checks found no newer compatible release of those packages at this check.

- Most entries propagate findings in npm's bundled brace-expansion, http-cache-semantics, ip-address and undici through their dependents. The project's explicit plugin list does not enable @semantic-release/npm and publishes no npm package. Those bundled packages have upstream advisory fixes, but the installed npm bundle was not changed by a compatible audit fix. These findings remain open; they are not described as resolved.
- The additional [braces stack-exhaustion advisory](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm) affects the installed latest braces 3.0.3. Its release-tool use matches repository-controlled branch/asset patterns. This workflow does not accept external patterns or process user app/auth data through that matcher. No patched compatible braces version was available at this check.
- The release job runs in a fresh GitHub Actions workspace, uses the built-in repository-scoped token, disables install scripts and uses a pinned changelog dependency. All audited npm packages are build/release tools; none is included in the .mpp or target app. No new secrets or workflow permissions were introduced for the browser candidate.

The bounded release use was reviewed before publication. Recheck the advisories before later releases and update compatible fixes when available. The increased audit count is not evidence of an authentication issue or a device login result.

Release v1.0.0-dev.1 initially also included a stale preflight `patches-1.0.0.mpp` beside the correctly versioned artifact. Its official metadata pointed to the correct dev bundle. The workflow now cleans the preflight output before semantic release to ensure later releases contain only their matching version; use the newest prerelease.

## Offline build with a warm dependency cache

The upstream plugin requires nonempty repository credential properties even offline. With dependencies already cached, use literal dummy values instead of accessing a real credential:

```sh
./gradlew :patches:test :patches:buildAndroid --offline --no-daemon -Pgpr.user=offline -Pgpr.key=offline
```

These values grant no access. Keep `--offline`; a first build still needs dependency setup as described above.

Diagnostic collector checks: `python3 -m unittest discover -s scripts -p 'test_trace*.py'`. The Kotlin tests require the published collector allowlist to equal the complete compiled marker vocabulary. The local pinned-APK tests also resolve every helper field and raw-status getter against the original DEX, and compare all original control flow with tracing enabled/disabled.
