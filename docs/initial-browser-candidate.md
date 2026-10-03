# Initial browser route candidate — experimental prerelease

This is a reviewable, default-off candidate distributed through the experimental `dev` release workflow. **Dev.8/Dev.9 accept WelcomeDisclosure only; the local next-release candidate also accepts Modal.** Its purpose is explicit selection of the client's existing complete generic web login route before native authorization. It does not resolve or reinterpret a native password rejection. No device login, installation or runtime acceptance is claimed.

## Option and exact scope

The existing **Preserve ChatGPT login callback (experimental)** patch has a new boolean option:

- Key: `preferInitialBrowser`.
- Title: **Prefer browser for initial email login (experimental)**.
- Default: **false**; the helper and route injection are absent when off.
- Input remains exactly **ChatGPT 1.2026.265 / 2626541**, with the analyzed base SHA256 in provenance.md. This does not enable newer APKs or other variants.

When on, the predicate selects the ordinary top-level web setup only if every condition holds: exact generic `ub6.e` configuration, interactive mode, no existing credential object, source `fa6.e` (WelcomeDisclosure) or `fa6.b` (Modal), and a non-null genuine scope without a reauthentication binding. This uses the existing generic welcome action and the pinned-APK modal “Log in another way” action proved in browser-top-level-entry.md. Both source identities retain every other predicate guard. It does not replace Google sign-in or intercept a password-submit coroutine. LoginMenu/LandingScreen, account selection, MFA, SSO retrigger, deep links, silent flows, existing credentials and reauthentication retain their original route decision.

## Bounded implementation

`InitialBrowserRoute.kt` verifies five original class hashes (`xb80`, `yj40`, `fa6`, `ub6`, `vb80`), the full method signature, register count and injection anchor before editing. At the first-invocation selector in `xb80.c`, it inserts five instructions and adds a private pure predicate in the already initialized caller class. Only a matching predicate clears the local native-eligibility flag. The existing branch then enters its original full web setup block.

No argument, source enum, silent/provider flag, continuation, scope, input credential or server response is overwritten. The original scratch register is overwritten by both ordinary successors before they use it; this liveness fact is checked against the pinned bytecode. The predicate has no external method calls, logging or writes. It checks only object identity/nullness and the absence of the existing reauthentication binding.

The original top-level controller continues to own loading, cancellation, suspension, credential storage and errors. Its web route retains active-scope validation, genuine signing-key acquisition and the complete `ehu0.h` pipeline: preauth/integrity handling, PKCE/state/nonce, the existing callback checks and token exchange. The existing callback preservation remains the only redirect correction. No security gate, rejected request or metadata value is fabricated or converted into success.

## Static tests

The verification runs against the private pinned APK without executing app code:

- The compiled injected predicate and branch are interpreted against **1,320 synthetic combinations** of eligibility, generic/social/provider configuration, source, silent mode, credentials and fresh/reauth/absent scope. The matrix also runs on the actual re-encoded candidate DEX in both candidate integration configurations. All original arguments are checked unchanged.
- The actual DEX coroutine dispatch is traversed: state 0 can reach the new selection; all eight resume states cannot. Existing original branch targets, registers, operands, switch destinations and exception boundaries are checked after removing only the exact validated injection.
- The helper's exact body/signature/flags are checked. All original methods and class/method/field metadata in `xb80` are independently verified. Morphe's mutable proxy changes binary representation even for an unchanged copy, so the test checks bodies by normalized instruction/control-flow comparison and metadata through a canonical reconstruction. This representation handling is confined to the injection class.
- **Selected classes** cover the route plus native requests, security and callback code. Default-off and trace-only retain the prior canonical/trace differential. Candidate mode verifies its exact bounded delta, then runs that same differential. The entire web/security pipeline and non-target classes are retained.
- Four pinned-APK configurations recompile resources: default, diagnostics only, browser preference only, and both options together. An explicit test verifies the preference defaults to false and another rejects a changed top-level method contract.

## Remaining limits

Email/password acceptance on the hosted page, clone signing/integrity acceptance, callback return to this clone and completed session storage still require a manual device test by the user. No credentials are requested or entered by the development tools. The general OpenAI web configuration is distinct from Google's; it does not guarantee which controls a live hosted page offers. Server/preflight failure continues to propagate.

The fallback custom-scheme callback shares its initial Android filter with the original app. The Auth Tab redirect scheme and callback validators are unchanged. This candidate does not claim to resolve that fallback's app-selection ambiguity. Diagnostics remain fixed-category and default-off; the local extension distinguishes static source identities and mirrors Modal eligibility. No account data or captured URLs are added.

Source history contains only patch source, tests and documentation. The existing authorized workflow publishes only the project patch bundle and generates matching Morphe metadata. No full APK, decompiled method body, raw log, token, signing secret or workflow secret is committed or uploaded. The development tools do not install, start or log into an app.

For the manual device test, refresh the `dev` source, select **Preserve ChatGPT login callback (experimental)**, enable **Prefer browser for initial email login (experimental)**, and keep **Update permissions** and **Update providers** enabled. Use the pinned APKM and the same clone package/signing key when updating an existing clone. Start with the generic login action on the original welcome screen or “Log in another way” in the modal; other sources intentionally preserve their existing route. Report only browser opening, availability of email/password, callback destination and visible error category. Do not publish credential values, authorization URLs, tokens or raw logs.

Local validation completed: **28 Kotlin tests** passed with no failures or skips, including all four pinned-APK/resource-recompiling configurations and the compiled-bytecode matrix; **11 Python tests** passed; `:patches:buildAndroid` succeeded offline. These results validate static patch behavior and buildability, not a live login.

The Modal extension adds only a second accepted source identity in the pure predicate. Diagnostic guard markers mirror that same scope and now distinguish named non-target sources. Positive Modal and WelcomeDisclosure rows plus all provider/credential/silent/scope/reauth/source negatives run against compiled helpers and both actual re-encoded preference configurations. Only coroutine state 0 reaches selection; states 1–8 cannot. The native `INVALID_USERNAME_OR_PASSWORD` cause remains unresolved; this candidate fixes route eligibility only.
