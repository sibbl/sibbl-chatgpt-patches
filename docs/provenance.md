# Provenance — checked 2026-10-02

## Official build sources

- [Morphe Patches v1.45.0](https://github.com/MorpheApp/morphe-patches/releases/tag/v1.45.0), released 2026-10-02 08:55 UTC, commit `7387cc19e7d43b5dc35c6ecbfac0bbc2282a094e`.
- [Clone app source at that commit](https://github.com/MorpheApp/morphe-patches/blob/7387cc19e7d43b5dc35c6ecbfac0bbc2282a094e/patches/src/main/kotlin/app/morphe/patches/all/misc/clone/CloneAppPatch.kt). Extracted ChatGPT manifest path; original copyright block retained. Local changes documented in source and auth-analysis.md.
- [Official template](https://github.com/MorpheApp/morphe-patches-template/tree/57538a3b85ccd3c9a24ed3cc06ec722787563879), commit `57538a3b85ccd3c9a24ed3cc06ec722787563879`.
- Manager 1.33.0; Patcher 1.15.0; patches Gradle plugin 1.3.4; Java 21; Gradle wrapper 9.8.0 with upstream distribution SHA256.
- [Patcher lifecycle/API](https://github.com/MorpheApp/morphe-patcher/blob/main/docs/2_2_patch_anatomy.md): finalizers run in reverse dependency order. The integration test checks the final output.
- [Manager source format](https://github.com/MorpheApp/morphe-manager/blob/main/docs/patch-sources.md): repository source with generated patches-bundle.json and .mpp release asset.

## APK input

User supplied the APKMirror download locally after automated download attempts failed. No APK is in this repository or any release.

- [APKMirror exact variant](https://www.apkmirror.com/apk/openai/chatgpt/chatgpt-1-2026-265-release/chatgpt-1-2026-265-7-android-apk-download/).
- Uploaded 2026-09-29 16:41:13 UTC; newest listed release/versionCode checked on 2026-10-02.
- Filename: `com.openai.chatgpt_1.2026.265-2626541_4arch_6dpi_24lang_602b18a1d458e3b1e4732387cf494fc1_apkmirror.com.apkm`.
- APKM size: 84,665,448 bytes; base + 34 splits. Architectures: arm64-v8a, armeabi-v7a, x86, x86_64. Densities: 120–480 dpi. No device ABI was assumed; universal bundle chosen for static analysis.
- APKM SHA256 (computed, matches APKMirror): `573ff883662cda4637435755b1e897e1f35ced830c6c99407c7c2abad5c32bba`.
- Extracted base.apk SHA256: `979d758415b99ecf05118bce536b4e5a9eb9ea68ebdf77bbf064a57bb924d771`.
- Actual manifest: `com.openai.chatgpt`, versionName `1.2026.265`, versionCode `2626541`, minSdk 32, targetSdk 37.
- apksigner verification passed. Signer SHA256: `b24f4bfbb3cf293f938703b9d87027c1102cc36dc4fa206910e08927db40473c` (matches APKMirror).
- Jadx 1.5.6 from [official skylot/jadx release](https://github.com/skylot/jadx/releases/tag/v1.5.6), ZIP SHA256 `545ea2be9c242511bc145755cf4bda2485ade42966e096f8b4d3da2a230e8974` checked against GitHub asset digest. Android SDK build-tools 36.1.0 used for aapt2/apksigner.

A signing-certificate fingerprint is distinct from a file hash. Version 1.2026.265 also exists with codes 2626526 and 2626527; those builds are not supported by this patch.
