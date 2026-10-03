# sibbl ChatGPT patches

Experimentelle Patches für **Morphe**, um eine zweite ChatGPT-App mit getrennten Appdaten zu testen. Unabhängiges Projekt von sibbl, nicht von OpenAI oder Morphe.

**Status: statisch geprüfter Callback-Fix, noch kein bestätigter Geräte-Login.** Der Nutzer bestätigt inzwischen einen erfolgreichen Patchbuild, beim Login erscheint aber „Incorrect email address or password“. Ein frischer E-Mail/Passwort-Login im privaten Browser funktioniert. Die Ursache des App-Fehlers ist offen; siehe [Differentialanalyse](docs/auth-differential.md). Der Patch bewahrt die ursprüngliche OAuth-Redirect-URI. Er deaktiviert weder PKCE/state/nonce noch Play Integrity, Signaturprüfungen oder TLS. Eine mögliche serverseitige Ablehnung wird nicht umgangen.

## Experimenteller Browser-Kandidat

Das neue Prerelease enthält die standardmäßig ausgeschaltete Option **Prefer browser for initial email login (experimental)** für den bestehenden generischen Erstanmeldeweg vom Begrüßungsbildschirm. **dev.7 enthält diese Option noch nicht**; die Quelle vor dem Build aktualisieren und die Option ausdrücklich aktivieren. E-Mail/Passwort-Akzeptanz und Rückkehr zum Klon bleiben ungeprüft; [Umfang, Tests und Grenzen](docs/initial-browser-candidate.md).

## In Morphe testen

Die Quelle enthält ausschließlich experimentelle Prereleases. Für den Erstimport unter **Sources → + → Remote** diese Metadaten-URL eintragen und als Namen **sibbl ChatGPT patches** verwenden:

`https://raw.githubusercontent.com/sibbl/sibbl-chatgpt-patches/refs/heads/dev/patches-bundle.json`

1. Die oben genannte Remote-Quelle hinzufügen. Sie bleibt ausdrücklich auf dem experimentellen `dev`-Zweig.
2. **Experimental app versions** aktivieren und die Quelle aktualisieren. Die direkte `dev`-Quelle braucht keinen Prerelease-Umschalter. Bei Bedarf Expert-Modus verwenden (beide Patches sind standardmäßig abgewählt).
3. Die heruntergeladene Original-APKM **ChatGPT 1.2026.265 (versionCode 2626541)** über die Dateiauswahl auswählen — eine neuere installierte ChatGPT-App ist kein unterstützter Eingang. [Passenden Download auf APKMirror öffnen](https://www.apkmirror.com/apk/openai/chatgpt/chatgpt-1-2026-265-release/chatgpt-1-2026-265-7-android-apk-download/). Auch andere Varianten mit demselben Versionsnamen, etwa Code 2626526 oder 2626527, sind nicht freigegeben. Das untersuchte Universal-Bundle benötigt Android 12L/API 32 oder neuer. Morphe wählt/vereinigt passende Splits; eine isolierte `base.apk` ist keine vollständige Installationsdatei.
4. **Preserve ChatGPT login callback (experimental)** wählen. Dessen Abhängigkeit enthält die Clone-Basis. Den universellen **Clone app**-Patch nicht zusätzlich wählen.
5. **Package name** standardmäßig `com.openai.chatgpt.clone` (Originalpaket + `.clone`), frei konfigurierbar. **App name** standardmäßig `ChatGPT clone`, frei konfigurierbar inklusive Unicode und Sonderzeichen; leere Namen oder ungültige Unicode-/XML-Zeichen führen zum Abbruch. **Update permissions = true** und **Update providers = true** sind Pflicht und standardmäßig aktiviert; Abschalten führt zum Abbruch.
6. Für den Browser-Kandidaten **Prefer browser for initial email login (experimental) = true** setzen. **Diagnostic auth tracing (no secrets)** kann ausgeschaltet bleiben.
7. APK in Morphe bauen und selbst auf dem Gerät testen. Ein vorhandener Klon unter einem anderen Paketnamen wird dadurch nicht aktualisiert. Insbesondere ist der neue Default eine separate Installation neben einem bisherigen `app.sibbl.chatgpt.private`-Klon; es erfolgt keine automatische Deinstallation. Original `com.openai.chatgpt` und seine Daten werden nicht ersetzt.

Alternativ die `.mpp` vom neuesten [Prerelease](https://github.com/sibbl/sibbl-chatgpt-patches/releases) als lokale Quelle importieren. Der reguläre [GitHub-Add-source-Link mit Quellennamen](https://morphe.software/add-source?github=sibbl/sibbl-chatgpt-patches&name=sibbl%20ChatGPT%20patches) verwendet den separaten **Pre-release patches**-Schalter: Ohne ihn lädt Morphe `main`, wo noch kein Stable-Release verfügbar ist. Der direkte `refs/heads/dev`-Link verhindert diese Umschaltung und eignet sich deshalb für den Erstimport. Die GitHub-Repository-Beschreibung ist vorhanden; Morphe bezieht den Bundle-Namen aus dem `.mpp`-Manifest. Ohne Geräteansicht ist die konkrete Ursache einer unvollständigen Anzeige noch nicht bestätigt.

Den Browser-Kandidaten vom ursprünglichen Begrüßungsbildschirm über den allgemeinen Anmeldeweg testen; Google-Anmeldung und ein bereits geöffneter nativer Passwortdialog sind nicht sein Einstieg. Für ein Update des vorhandenen Klons dessen bisherigen Paketnamen und denselben Morphe-Signierschlüssel verwenden. Bitte nur berichten, ob sich der Browser öffnet, ob er E-Mail/Passwort anbietet, welche App nach der Anmeldung zurückkehrt und welche Fehlermeldung erscheint.

Beide Apps beanspruchen weiterhin den ursprünglichen Login-Callback. Wenn Android eine Appauswahl zeigt, den **Klon** wählen. Callback-Routing, Browser/Auth Tab, Integritätsprüfung und vollständiger Login sind noch auf dem Gerät zu prüfen. Bitte nur Fehlerphase (vor Browser / im Browser / nach Rückkehr), Android-/Browser-Version und Fehlermeldung melden — keine Auth-URLs mit Parametern, Tokens oder Rohlogs veröffentlichen.

## Optionale Login-Diagnose

Im Callback-Patch gibt es **Diagnostic auth tracing (no secrets)**, standardmäßig **aus**. Aktiviert protokolliert sie unter `SibblAuthTrace` ausschließlich feste Phasen, Fehlerkategorien, echte native HTTP-Statuscodes sowie Seitentypen vor und nach der Antwortverarbeitung. Strukturierte Fehlercodes werden nur mit einer festen Liste verglichen; unbekannte Werte bleiben `OTHER`. Keine Zugangsdaten, Servertexte, Auth-URLs oder Uploads. Die Diagnoseoption beobachtet nur; die getrennte Browser-Option bleibt ein ungeprüfter Login-Kandidat. [Aktivierung, Beobachtung und Bedeutung der Marker](docs/diagnostics.md).

## Was geändert wird

- Clone-Manifestlogik auf Basis des offiziellen **Clone app** aus Morphe Patches **v1.45.0**.
- Anwendungs- und Launcher-Namen einschließlich Launcher-Aliases werden auf eine eigene String-Ressource gesetzt. Explizite Komponentenlabels, die den ursprünglichen Appnamen verwenden, folgen ebenfalls dem gewählten Namen; andere Funktionsbezeichnungen bleiben erhalten.
- Custom Permissions und installierte Provider-Authorities werden getrennt benannt. Referenzen an geschützten Komponenten werden ebenfalls angepasst; fremde Provider unter `<queries>` bleiben unverändert.
- Genau zwei Rückgabewerte von `getPackageName()` im analysierten OAuth-Konfigurationsaufbau werden durch `com.openai.chatgpt` ersetzt. Ein dritter Aufruf für die tatsächliche Appidentität bleibt dynamisch.
- Die ausgeschaltete Browser-Option ändert keinen Anmeldeweg. Aktiviert wählt sie ausschließlich im geprüften generischen Erstanmeldekontext den vollständigen vorhandenen Webweg; Scope, Argumente und Sicherheitsprüfungen bleiben erhalten.
- Exakte Versions- und Bytecode-Guards brechen bei abweichendem Eingang ab.

Der optionale Patch **Clone ChatGPT (experimental baseline)** allein dient nur dem Vergleich und enthält den Callback-Fix nicht.

<!-- PATCHES_START -->
> **[v1.0.0-dev.9](https://github.com/sibbl/sibbl-chatgpt-patches/releases/tag/v1.0.0-dev.9)**&nbsp;&nbsp;•&nbsp;&nbsp;`dev`&nbsp;&nbsp;•&nbsp;&nbsp;2 patches total
<details open>
<summary>📦 ChatGPT&nbsp;&nbsp;•&nbsp;&nbsp;2 patches</summary>
<br>

**🎯 Supported versions:**

| 🧪&nbsp;1.2026.265 |
| :---: |
| Only versionCode 2626541. Download APKM: https://www.apkmirror.com/apk/openai/chatgpt/chatgpt-1-2026-265-release/chatgpt-1-2026-265-7-android-apk-download/ APK statically tested; installation and login remain unverified. |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Clone ChatGPT (experimental baseline)](#clone-chatgpt-experimental-baseline) | Separate package with mandatory permission and provider renaming. Authentication is unresolved: this patch does not fix invalid auth request. Do not combine with the universal Clone app patch. | • Package name<br>• App name<br>• Update permissions<br>• Update providers |
| [Preserve ChatGPT login callback (experimental)](#preserve-chatgpt-login-callback-experimental) | Keeps the original OAuth redirect URI when cloning. Includes the clone baseline. Login and server acceptance remain unverified. Optional default-off initial browser route and fixed-category auth diagnostics. | • Diagnostic auth tracing (no secrets)<br>• Prefer browser for initial email login (experimental) |

</details>

<!-- PATCHES_END -->

## Unterstützter Analyse- und Teststand

| Bestandteil | Version / Status |
|---|---|
| ChatGPT | 1.2026.265, Code 2626541; ausschließlich dieser Eingang |
| Morphe Manager | 1.33.0 als aktueller Stable-Stand geprüft |
| Offizielle Patches / Patcher / Buildplugin | 1.45.0 / 1.15.0 / 1.3.4 |
| Tests | Synthetische Manifesttests und lokaler statischer APK-Integrationstest |
| Gerät / Login | Nutzer bestätigt Installation von dev.4; meldet „Incorrect email address or password“. Login weiterhin unbestätigt. Entwicklung hat keine App installiert/gestartet. |

[Analyse und Belegstellen](docs/auth-analysis.md) · [Herkunft und Prüfsummen](docs/provenance.md) · [Build und Veröffentlichung](docs/development.md)

## Lokal bauen

Für die aktuellen experimentellen Patches den `dev`-Zweig auschecken (`git checkout dev`); `main` ist noch kein veröffentlichter Stable-Stand. Java 21 und bestehende GitHub-Packages-Leseberechtigung gemäß [Morphe-Dokumentation](https://github.com/MorpheApp/morphe-documentation) verwenden. Zugangsdaten nur im Benutzerprofil oder Prozess, nie im Repository speichern.

```sh
./gradlew :patches:test :patches:buildAndroid
```

Ergebnis: `patches/build/libs/patches-*.mpp`. Ein `.mpp` enthält Patches, keine ChatGPT-APK. Ohne `CHATGPT_TEST_APK` überspringt der Build den lokalen APK-Test; CI verwendet nur freie synthetische Fixtures.

## Lizenz und Herkunft

GPL-3.0 mit den in [NOTICE](NOTICE) erhaltenen Hinweisen. Basierend auf [Morphe Patches](https://github.com/MorpheApp/morphe-patches) und dem [offiziellen Template](https://github.com/MorpheApp/morphe-patches-template), ihrerseits mit ReVanced-Herkunft. Originalhinweise sind erhalten; Änderungen von sibbl sind markiert. OpenAI-Appdateien und dekompilierter Appcode gehören nicht zu diesem Repository und werden nicht mitgeliefert.
