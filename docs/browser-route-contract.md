# Browser route entry review — 2026-10-02

Status: **no browser-selection patch implemented or published**. The latest released patch remains v1.0.0-dev.7. This review uses only the pinned ChatGPT 1.2026.265 / 2626541 APK. No APK is executed, installed, or uploaded; no OAuth request, account change, or further device observation is performed.

## Email/password requirement

The user wants email/password authentication, not Google sign-in. The existing generic configuration `ub6` has an empty provider and `login_or_signup` screen hint. The distinct `vb6` configuration explicitly names `google-oauth2`. `qd80.m` with the existing mask 60 defaults its provider-selection boolean to false and requests the interactive mode. `qd80.l` selects `ub6` when this boolean is false. Its other boolean, taken from `qd80.q`, is a separate argument and must not be mistaken for the provider selector.

This establishes a generic OpenAI-hosted login route in the client rather than a Google substitution. It does not establish that the live hosted page will offer/accept email/password for the cloned installation. The user's successful independent browser login is separate evidence and does not prove this app flow.

## Concrete entry blockers

| Proposed shortcut | APK evidence | Why it is not implemented |
|---|---|---|
| Jump to the email/Continue method's existing browser fallback | Next onboarding viewmodel `b.M` classifies `AuthorizeChallengeException`, consults `tg90.t`, then calls `qd80.m`. Its own coroutine uses `te80` and returns Unit. | An unconditional jump would discard the fallback condition and the established coroutine entry/state handling. No gate is forced true. |
| Replace the native password request with a browser call | `rh80.invokeSuspend` calls `jd80.i`, then interprets success as a `cf6` page. Browser `qd80.l` completes with `nnq0<Unit>` after ordinary credential storage and completion routing. `t5j` is another caller of the password method. | A call-site swap breaks the result contract. It can cast Unit to a native page or resume the wrong continuation. Returning a fabricated page would not be acceptable. |
| Invoke the full browser route immediately at view creation | `qd80.l` acquires the ordinary signing key, reads the current `ef80` stack item and `df80`, appends ExternalUrl under an identity check, and dereferences the previous step for flow analytics. The stack is created empty by the controller. | The route is not a standalone startup API. A valid active scope, nonempty initialized stack, cancellation/loading ownership, and the UI effect callback must be established for a new explicit action. This has not been proved for a new action before native authorization. |
| Reuse the native error view's action on the password page | Existing `ib80` → `cb80`/`tt1` invokes the browser route for an `i6u` error page. The observed native response was a `jm40` password page. Other existing callers belong to different page viewmodels. | There is no verified general browser-choice action on the observed page. Server page transitions/errors are not fabricated or reclassified. |

These are implementation blockers for the candidate examined here, not proof that every explicit route choice is impossible. The next prerequisite is a separately proved user-action entry with its own correctly adapted suspension/result and loading/cancellation contract. It must invoke the entire normal browser pipeline and keep all existing security and server error handling intact. Until that entry is established, there is no safe tested candidate to publish.

## Security and callback limits

No change is made to preauth/integrity, PKCE/state/nonce, genuine app signing identity, DPoP, cookies, token exchange, or server error handling. The browser pipeline and its original callers remain unchanged except for the already released opt-in fixed-category diagnostics.

The existing callback patch preserves the original registered redirect URI. The Auth Tab scheme is derived from that URI. The original app and clone both retain its custom-scheme manifest filter, so the fallback Custom Tabs callback's initial Android destination is not statically guaranteed. The receiving WebRedirectActivity explicitly forwards within its own app, and `ofu0.b` validates URI and state. No browser callback was observed in the password attempt. A return to the clone, hosted email/password acceptance, and a completed login therefore remain unverified.

## Reproducible static validation

The local-only APK integration test now compares 75 selected classes, extending the prior 56 with the entry callers, viewmodels, continuations and provider configurations discussed above. With tracing off, 74 are canonically identical to the original; only the intentional two-component redirect correction in `i280` differs. With tracing on, the integration comparator removes only allowlisted trace calls and checks original registers, operands, branches, switch destinations and exception boundaries.

`BrowserRouteContract.kt` additionally checks both original and patched DEX for the separate generic/Google configurations, existing signing-key acquisition, full authenticator invocation and credential-storage call, the browser Unit success payloads, the native password page cast, and the retained email-entry fallback gate. These are structural regression checks, not runtime login tests. Build instructions and the private APK hash remain in development.md and provenance.md. Proprietary method bodies and binaries are not included in this repository.

Local validation completed: all 19 Kotlin tests passed with the pinned APK (including both resource-recompiling integration configurations), all 11 Python collector tests passed, and `:patches:buildAndroid` succeeded offline. These results validate the existing patch plus the expanded static audit; they do not validate a browser-selection patch or a login.
