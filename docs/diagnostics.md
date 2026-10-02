# Opt-in auth diagnostics

This diagnostic prerelease does **not** add another login fix. Compatibility remains exactly ChatGPT **1.2026.265 / 2626541**. Dev.5 installed and the error appearing inside the clone are user-confirmed. The return to the original app during observation was manual, so it is not evidence of callback misrouting.

## Enable in Morphe

1. Refresh the existing `refs/heads/dev/patches-bundle.json` remote source and select the diagnostic prerelease.
2. Select the original supported APKM and **Preserve ChatGPT login callback (experimental)**.
3. Open that patch's options and enable **Diagnostic auth tracing (no secrets)** (`authTrace`). It defaults to **false**. Keep **Update permissions** and **Update providers** enabled in the clone dependency.
4. Build and install the clone yourself. Use the same package and Morphe signing configuration if updating an existing clone. The project does not install or start an app.
5. Coordinate one observation window before a further login attempt. No repeated retries are required to enable this option.

For a separately authorized ADB session, the collector must resolve the **exact clone package UID**, filter `SibblAuthTrace:I` and `*:S` on the device, and pass only the fixed marker allowlist through a device-side `case` filter. The host validates the same allowlist again before storing or displaying anything. Do not collect general logcat output, activity dumps, UI state, credentials, or authentication URLs.

The source-only collector components are [`trace_filter.py`](../scripts/trace_filter.py), [`trace_stream_parser.py`](../scripts/trace_stream_parser.py), and [`trace-allowlist.txt`](../scripts/trace-allowlist.txt). They do not connect, install, launch, or initiate login themselves. `device_command(uid, since, seconds)` produces a command bounded to at most 900 seconds. Resolve the UID using `exact_uid` on `cmd package list packages -U <exact package>` output; revalidate it after reinstalling. Stop an obsolete UID reader before starting a new bounded reader. A connection failure must be reported, not treated as an active capture. No log buffer is cleared and the patch adds no network telemetry.

To remove the diagnostic instrumentation, rebuild from the original supported APKM with `authTrace=false` and install that result yourself. Switching it off is a build-time choice, not an in-app setting. Old Android log-buffer entries may remain until normal rotation.

## Meaning of the markers

| Marker | What it establishes |
|---|---|
| `NATIVE_PASSWORD_SUBMIT` | Entry into the native password-submission method; no argument inspected. |
| `NATIVE_BEGIN_ENTER`, `NATIVE_STEP_ENTER` | Native repository entry; coroutine resumptions can repeat these markers. |
| `PAGE_PASSWORD`, `PAGE_ERROR`, `PAGE_OTHER` | Type of native page being converted for display. No page text is read. |
| `NATIVE_HTTP_DISPATCH` | Execution reaches the native next-step HTTP client's call. Unlike repository entry, coroutine resumption does not repeat this point. It does not prove a request reached the server or distinguish internal HTTP retries. |
| `NATIVE_RAW_HTTP_*` | Actual raw response status before native body decoding; includes 200/201/202/204/301/302, the listed error statuses, and OTHER. A suspended coroutine produces no status. |
| `NATIVE_TRANSPORT_*` | Parsed native transport wrapper before conversion to repository results. `OK` can contain an error page. |
| `NATIVE_BEFORE_MAP_PAGE_*`, `NATIVE_AFTER_MAP_PAGE_*` | ERROR, PASSWORD or OTHER page before/after repository mapping, independent of whether the display model is emitted again. |
| `NATIVE_BEFORE_MAP_TOP_*`, `NATIVE_BEFORE_MAP_NESTED_*` (and corresponding `AFTER_MAP`) | Presence of top-level/nested errors and bounded structured metadata classification. No display text. |
| `NATIVE_BEGIN_*`, `NATIVE_STEP_*` | Completed native result classification. Suspended coroutines and unrecognized non-result objects produce no result line. |
| `BROWSER_DISPATCH_ENTER`, `BROWSER_RESULT_*` | Browser flow entry and completed result, separate from the native password flow. Entry alone does not prove a browser actually opened. |
| `CALLBACK_RECEIVED` | Callback handler entered; it may still lack a pending request. |
| `CALLBACK_URI_MISMATCH`, `CALLBACK_STATE_MISMATCH` | The existing validation reached its corresponding failure branch. Validation is unchanged. |
| `CALLBACK_REMOTE_ERROR` | A matched callback contained an error parameter. Its code and description are not logged. |
| `CALLBACK_CODE_PRESENT` | Existing checks found a nonblank authorization code. The code is neither read by the helper nor logged. This is not proof of token exchange or completed login. |
| `TOKEN_EXCHANGE_ENTER`, `TOKEN_HTTP_*` | Token-exchange method entry and classification of the returned HTTP result wrapper. |
| `AUTH_UI_*` | Classification when the legacy native auth-error text mapper is called. Other native display paths may not use it. |

Result suffixes are fixed categories: `OK`, `FAILURE`, `WEB_AUTH_FAILED`, `INTEGRITY_FAILED`, `SIGNING_KEY_UNAVAILABLE`, `MISSING_RESPONSE`, `BROWSER_UNAVAILABLE`, `CANCELLED`, `NO_CREDENTIALS`, `AUTHORIZE_CHALLENGE`, or `IO_ERROR`. **`OK` means a successful result wrapper at that point, not authenticated account access.** For example, a native response can successfully return another password/error page.

HTTP status categories are limited to `HTTP_400`, `401`, `403`, `404`, `408`, `409`, `422`, `429`, `500`, `502`, `503`, `504` (each with the `HTTP_` prefix), and `HTTP_OTHER`. These describe a status, not its cause: HTTP 403 alone does not establish an integrity rejection. Unknown exceptions use `FAILURE`; unknown/missing result values are not stringified. A marker can occur more than once; no per-user/session identifier is added.

Metadata suffixes are `ABSENT`, `PRESENT`, `METADATA_EMPTY`, `METADATA_TRUNCATED`, `CODE_NONE`, `CODE_OTHER`, and `CODE_CREDENTIALS`, `CODE_REQUEST`, `CODE_GRANT`, `CODE_CLIENT`, `CODE_RATE_LIMIT`, `CODE_ACCESS_DENIED`. The comparison dictionary in `NativeResponseTrace.kt` is a diagnostic allowlist, **not evidence that the server uses or returned those codes**. Only exact equality with listed literals selects a named category. Unknown codes remain OTHER. At most 16 metadata entries per error location are inspected; no code, type, field-error message, form-error message, title, description, identifier or payload is logged. Categories alone do not establish why a request was rejected.

## Grounded injection points

The pinned binary contains both browser auth and native first-party onboarding:

- `jd80.i` submits `LoginPassword` with a password-request object through the native repository. The trace inserts a zero-argument marker at entry; it never reads that object's contents or the method's password argument.
- `com.openai.feature.onboarding.impl.next.repository.a.a/e` return native result wrappers. Native configuration `pa80` derives the next-step path `api/first_party_authorize/next` from the existing auth base. Diagnostic code does not read or alter requests, headers, URLs, or configuration.
- `fy0.invokeSuspend`'s native `pa80` branch calls `mhy.c`. Its branch-local return is shared by initial and resumed completion. The raw-response helper ignores the suspension sentinel, calls the status getter `igy.f`, and reads only numeric `nhy.a`. No other coroutine branches are instrumented.
- The native repository's existing `e580` cast identifies the parsed transport result. Successful `d580.a` holds the pre-map page; returned `nnq0.b` holds the post-map page. Both are classified independently of UI updates.
- Error page `i6u` contains top-level errors `c` and nested `b.d`; password page `jm40` contains top-level `c` and nested `b.a`. `ue6.d` is structured metadata, with `re6.a` as its code. The serializers distinguish these fields from title, description, fieldErrors and formErrors. Only that code field is compared; the others are not read.
- `qd80.a` maps a password page (`jm40`) and error page (`i6u`) to native display models. Error-page metadata can supply text; password-page validation metadata can also supply errors. Display-model tracing classifies only the page class. Separate response tracing inspects structured error-code metadata by exact comparison to a fixed dictionary.
- `com.openai.auth.a.b` can display `AuthError.WebAuthFailed`'s external error description. `ofu0.b` supplies this only after its existing callback validation. Thus text absent from local resources can still appear inside the app.
- `u56.e` processes the native token endpoint response; a marker follows the existing response-wrapper cast. No response-body field is accessed.

The literal “Incorrect email address or password” was not found in the previously examined DEX/resources table. The previous marker sequence reached native password submission, a successful native result wrapper and a native error page. Later submissions returned successful wrappers without another UI page marker. This supports tracing each response before/after mapping; it does not prove a 200 status, credential mismatch, integrity rejection, or successful authentication. Repository entry markers can repeat on resumption; do not count them as independent requests.

## Privacy and behavior checks

All emitted messages and the tag are hardcoded string literals. Helpers inspect allowlisted types, result/error wrapper references, numeric HTTP status and the specific structured error-code field described above. Code strings are used only in exact comparisons with fixed literals. Helpers never invoke exception text methods, stringify runtime objects, inspect display strings, or hash runtime data. Fingerprint hashes used at **patch time** cover static APK classes only.

Helpers are static methods added to the already initialized caller class. Calls do not change the caller's registers, return values, validation branches, or network logic. Helpers catch their own failures and return without replacing the app's error. The app's pre-existing logging is not modified; the guarantees here cover only this patch's dedicated tag.

Synthetic tests execute the generated DEX branches for all status/code buckets, absent/empty/unknown/truncated metadata, coroutine suspension, and helper failures. Collector tests cover split streams, duplicate lines, rotation, disconnects, unknown values, UID scope and command injection. Tests compile every helper, restrict all field/method references and logged literals, verify default-off behavior, and apply both configurations to the privately held exact APK. The integration comparison removes only the added helper calls and checks original instruction operands, branch destinations, switch destinations, registers, and exception-handler boundaries. Normal DEX payload-alignment padding is normalized. No APK, decompiled code, or captured device logs are distributed. Android execution and login success remain unverified for this diagnostic build.

## Latest bounded device finding

With Dev.6, the new dispatch and pre/post-map markers were observed. The native password response contained structured `CODE_CREDENTIALS` categories at both error locations, already before mapping and preserved afterwards. This locates the reported rejection in the processed native response; it does not establish incorrect user input or why that response was returned. No browser/callback/token markers were observed in that attempt. Raw HTTP status was not captured: the resumed coroutine jumped directly to the original return and skipped the inserted status call. The corrected diagnostic anchor retains incoming branch labels on the helper, with synthetic fresh/resumed tests and a pinned-APK check that every original incoming branch reaches it. No authentication logic is changed and no HTTP status or login success is inferred from the earlier `OK` wrapper.
