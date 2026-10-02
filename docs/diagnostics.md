# Opt-in auth diagnostics

This diagnostic prerelease does **not** add another login fix. Compatibility remains exactly ChatGPT **1.2026.265 / 2626541**. Dev.4 installed and the error appearing inside the clone are user-confirmed. The return to the original app during observation was manual, so it is not evidence of callback misrouting.

## Enable in Morphe

1. Refresh the existing `refs/heads/dev/patches-bundle.json` remote source and select the diagnostic prerelease.
2. Select the original supported APKM and **Preserve ChatGPT login callback (experimental)**.
3. Open that patch's options and enable **Diagnostic auth tracing (no secrets)** (`authTrace`). It defaults to **false**. Keep **Update permissions** and **Update providers** enabled in the clone dependency.
4. Build and install the clone yourself. Use the same package and Morphe signing configuration if updating an existing clone. The project does not install or start an app.
5. Coordinate one observation window before a further login attempt. No repeated retries are required to enable this option.

With an already authorized ADB connection, read **only** the dedicated tag:

```sh
adb -s YOUR_DEVICE logcat -v raw -s 'SibblAuthTrace:I' '*:S'
```

Start before the coordinated attempt, then stop with Ctrl-C. This command neither clears the log buffer nor requests other tags. Old entries may be present; use the newly produced sequence. Share only the fixed category lines described below, not a general bug report or other application logs. The patch adds no upload or network telemetry.

To remove the diagnostic instrumentation, rebuild from the original supported APKM with `authTrace=false` and install that result yourself. Switching it off is a build-time choice, not an in-app setting. Old Android log-buffer entries may remain until normal rotation.

## Meaning of the markers

| Marker | What it establishes |
|---|---|
| `NATIVE_PASSWORD_SUBMIT` | Entry into the native password-submission method; no argument inspected. |
| `NATIVE_BEGIN_ENTER`, `NATIVE_STEP_ENTER` | Native repository entry; coroutine resumptions can repeat these markers. |
| `PAGE_PASSWORD`, `PAGE_ERROR`, `PAGE_OTHER` | Type of native page being converted for display. No page text is read. |
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

## Grounded injection points

The pinned binary contains both browser auth and native first-party onboarding:

- `jd80.i` submits `LoginPassword` with a password-request object through the native repository. The trace inserts a zero-argument marker at entry; it never reads that object's contents or the method's password argument.
- `com.openai.feature.onboarding.impl.next.repository.a.a/e` return native result wrappers. Native configuration `pa80` derives the next-step path `api/first_party_authorize/next` from the existing auth base. Diagnostic code does not read or alter requests, headers, URLs, or configuration.
- `qd80.a` maps a password page (`jm40`) and error page (`i6u`) to native display models. Error-page metadata can supply text; password-page validation metadata can also supply errors. Only the page's class is classified.
- `com.openai.auth.a.b` can display `AuthError.WebAuthFailed`'s external error description. `ofu0.b` supplies this only after its existing callback validation. Thus text absent from local resources can still appear inside the app.
- `u56.e` processes the native token endpoint response; a marker follows the existing response-wrapper cast. No response-body field is accessed.

The literal “Incorrect email address or password” was not found in the previously examined DEX/resources table. These are possible display paths, not proof that a particular one produced the observed message. An embedded view cannot be excluded solely by the user's description or the Activity observation. The new marker sequence is intended to identify the actual path before proposing any further change.

## Privacy and behavior checks

All emitted messages and the tag are hardcoded string literals. Helpers can only inspect allowlisted class types, two error-wrapper references, and their numeric HTTP status fields. They never invoke exception text methods, inspect arbitrary error codes, read string-valued payload fields, or compute hashes of runtime data. Fingerprint hashes used at **patch time** cover static APK classes only.

Helpers are static methods added to the already initialized caller class. Calls do not change the caller's registers, return values, validation branches, or network logic. Helpers catch their own failures and return without replacing the app's error. The app's pre-existing logging is not modified; the guarantees here cover only this patch's dedicated tag.

Tests compile every helper, restrict all field/method references and logged literals, verify default-off behavior, and apply both configurations to the privately held exact APK. The integration comparison removes only the added helper calls and checks original instruction operands, branch destinations, switch destinations, registers, and exception-handler boundaries. Normal DEX payload-alignment padding is normalized. No APK, decompiled code, or captured device logs are distributed. Android execution and login success remain unverified for this diagnostic build.
