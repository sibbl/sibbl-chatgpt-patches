# Native auth and existing browser path — 2026-10-02

The Dev.7 attempt returned an actual HTTP 200 response followed by a native password page containing the fixed CREDENTIALS metadata category, before and after mapping. This identifies a rejection in the processed native response. It does not identify incorrect input, a rejected package, a certificate check, or an integrity decision. No further device capture or login is needed for this static investigation.

## Targeted differential

The pinned original is ChatGPT 1.2026.265 / 2626541, with the base SHA256 documented in provenance.md. `AuthDifferential.kt` now compares 56 selected classes, including native password models/serializers, the next-step request, HTTP-client construction and binding, response mapping, the login signing-key lease, and existing browser routing. Both real-APK configurations passed locally. With diagnostics off, the canonical DEX of all 55 classes other than the intentionally changed `i280` is identical. With diagnostics on, the existing integration check strips only our trace calls and compares original operands, registers, control flow and exception boundaries. This is static verification; it does not execute the APK or inspect account data.

| Difference | Evidence and native-request relevance | Limit |
|---|---|---|
| Password/request bytecode | `jd80.i` constructs `pm40`; `nm40` serializes it; `pb6`/`nb6` carry the native step and data. These classes are unchanged. | Actual inputs were not read. |
| Runtime package | Native `pa80.g` derives from `xl80.f`. The binding chain `nw4.n1` → `nit` → `e280.a` → `f280` installs the `zh7` default-request configuration, retained through `aw` and the native child client. It reads `w6k0.z` for OAI-Package-Name. The existing configuration projection keeps the actual clone identity. | This is a connected static construction path, not a wire capture or evidence that the server rejects the value. |
| Package and signing certificate | `l690.b` conditionally creates an SMS app_hash from the actual package and actual certificate and includes it in authorization parameters. `pa80.a` uses this same authorization builder. | Runtime flag and actual hash were not observed. No certificate or identity is forged. |
| Local auth/session state | Login scopes, cookie handling and signing-key acquisition use the separate installation's state. `uj40` creates a genuine new key alias and `rj40` leases the signing key; the request attaches the existing DPoP attribute. | Real cookies, identifiers, keys and tokens were not read. Separate state alone is not an error. |
| Permission/provider renames | Manifest and provider-resource changes isolate Android installation/IPC names. No such replacement is introduced into the password models or request bytecode. | Android credential/provider interactions can still depend on genuine app identity. |

## Existing browser OAuth route

A full interactive route exists: `qd80.m` → `qd80.l` → `ehu0.h` → the existing preauth/integrity and browser-dispatch pipeline. `qd80.l` obtains the existing signing-key lease, uses an optional username hint and calls the existing web authenticator. It is not a replacement password check.

The route preserves `ehu0`'s preauth/integrity handling, `l690`'s state/nonce/PKCE S256 generation, the corrected redirect configuration, `ofu0`'s URI/state checks, and normal token exchange/storage. These components are included in the differential. Calling a lower-level browser dispatcher while dropping that preparation would not reproduce the existing route and is not proposed.

Normal callers include `qd80.h` for the StartWebFlow/ExternalUrl transition, social-login fallback in `vgx`, and the native error view's WebFlow action (`ib80` → `w780`/`cb80` → `tt1` → `qd80.m`). Feature-specific authorize-challenge fallback also exists. There is no verified general browser-choice control on the observed password page: that response is `jm40`, not the `i6u` error page whose action maps to WebFlow.

The existing route is a grounded candidate for an explicit browser-login preference, with the entire ordinary pipeline intact. Before publishing such a change, its user-action binding and coroutine/result contract must be proved and tested. Its existence does not establish server acceptance of a signed clone. No feature/integrity override, synthetic server response, header substitution, or new patch release is included here. No immediate credential retry is justified.

## Account-separation alternatives

The user's confirmed working private browser session can coexist with the original work-account app. OpenAI currently documents two-account switching on ChatGPT web and explicitly says native mobile account switching is not yet supported: [account switching](https://help.openai.com/en/articles/20001068-use-multiple-accounts-with-account-switching).

For two native app instances, Android work/personal profiles use the same APK with isolated app data and separate launcher entries: [Android Enterprise developer guide](https://developer.android.com/work/guide). This avoids changing the package and signing certificate. Profile availability and the exact setup on the user's device are not established; the minimum context for that alternative is the device model and whether a work profile already exists. No profile is created or changed by this project.
