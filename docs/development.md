# Development and release

## Build

Use JDK 21. The official Gradle plugin resolves Patcher and smali from GitHub Packages/JitPack. Existing package-read credentials may be supplied through `GITHUB_ACTOR` and `GITHUB_TOKEN` or private user Gradle properties `gpr.user` / `gpr.key`. Never put credentials in this repository. No new PAT or workflow secret is needed for GitHub Actions; the workflow uses the repository-scoped built-in GITHUB_TOKEN.

```sh
./gradlew :patches:test :patches:buildAndroid --no-daemon
```

## Optional real-APK static integration test

Keep the APK outside the checkout. Set `CHATGPT_TEST_APK` to the extracted **base.apk** from the exact bundle in provenance.md, then run the same build command. The test verifies the base SHA256, applies the real patches, recompiles resources, rereads output DEX and asserts the two callback overrides while the third package read remains dynamic. Temporary output remains in an OS temporary directory; the test never installs, launches, signs or uploads an APK.

```sh
CHATGPT_TEST_APK=/absolute/private/path/base.apk ./gradlew :patches:test :patches:buildAndroid --rerun-tasks
```

For CI this variable is absent, so the proprietary-APK test is skipped explicitly. Synthetic tests still run. Local integration success is documented separately; CI success alone must not be represented as Android/login verification.

## Distribution

The release.yml/.releaserc pipeline is adapted from the official template. Changes: Actions pinned to verified commit IDs; Node 24; locked npm install without lifecycle scripts; explicit source-only check and tests before release; independent project identity; unused automatic PR workflow and example extension removed. Both GPL notices are bundled under licenses/ (the upstream plugin excludes META-INF notices).

The template changelog dependency is pinned to its existing lockfile commit. Gradle wrapper verifies the distribution SHA256. Dependencies are ordinary build tools; no custom network/auth endpoint or account secret is added.

Develop on `dev` and use conventional commits. `feat:`/`fix:` create **prereleases** on dev; `chore:` does not. The initial main branch is a source-only bootstrap. Do not merge experimental changes to main until device results justify a stable release. Do not upload releases manually or hand-edit generated patches-bundle.json, patches-list.json, CHANGELOG.md. Semantic release generates .mpp and matching metadata in the official format.

Before any push, run `python3 scripts/check_source_only.py` after staging. It rejects APK/APKM/DEX, compiled patch files, decompiled source markers, raw logs and common secret formats. The official Gradle wrapper JAR is the sole permitted infrastructure binary in source history. Release assets contain only the project patch bundle, never a full app.

## Future app versions

Do not relax version guards from a version-name match alone. Verify the new binary hash, signer, versionCode, request constructor and final manifest. Recheck all callback paths, PKCE/state behavior and provider/permission references. Extend the exact fingerprint and real-APK test only from evidence. Never spoof Integrity verdicts or discard authentication checks to make a test appear successful.

## Release dependency audit (2026-10-02)

Locked dependencies were installed with lifecycle scripts disabled. npm audit reported three findings (two high, one moderate) in dependencies bundled inside npm 11.21.0, pulled in by semantic-release's default npm plugin: brace-expansion, undici and ip-address. No compatible npm 11 update was available; audit fix could not change bundled dependencies. This project's explicit plugin list does not enable @semantic-release/npm and publishes no npm package. These tools are not included in the .mpp or target app. The remaining audit findings are tracked here rather than described as resolved. Recheck before future releases.
