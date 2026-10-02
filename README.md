# sibbl ChatGPT patches

Experimentelle Patches für **Morphe**, um eine zweite ChatGPT-App mit getrennten Appdaten zu testen. Unabhängiges Projekt von sibbl, nicht von OpenAI oder Morphe.

**Status: statisch geprüfter Callback-Fix, noch kein bestätigter Geräte-Login.** Der Patch bewahrt die ursprüngliche OAuth-Redirect-URI. Er deaktiviert weder PKCE/state/nonce noch Play Integrity, Signaturprüfungen oder TLS. Eine mögliche serverseitige Ablehnung wird nicht umgangen.

## In Morphe testen

Nach dem ersten erfolgreichen **Prerelease** dieses Repositories:

1. [Patchquelle hinzufügen](https://morphe.software/add-source?github=sibbl/sibbl-chatgpt-patches) oder `https://github.com/sibbl/sibbl-chatgpt-patches` als GitHub-Quelle eintragen.
2. Für diese Quelle **Pre-release patches** und **Experimental app versions** aktivieren und die Quelle aktualisieren. Bei Bedarf Expert-Modus verwenden (beide Patches sind standardmäßig abgewählt).
3. Original **ChatGPT 1.2026.265 (2626541)** als APKM auswählen. Das untersuchte Universal-Bundle benötigt Android 12L/API 32 oder neuer. Morphe wählt/vereinigt passende Splits; eine isolierte `base.apk` ist keine vollständige Installationsdatei.
4. **Preserve ChatGPT login callback (experimental)** wählen. Dessen Abhängigkeit enthält die Clone-Basis. Den universellen **Clone app**-Patch nicht zusätzlich wählen.
5. Paketname standardmäßig `app.sibbl.chatgpt.private`. **Update permissions = true** und **Update providers = true** sind Pflicht und standardmäßig aktiviert; Abschalten führt zum Abbruch.
6. APK in Morphe bauen und selbst auf dem Gerät testen. Ein vorhandener Klon unter einem anderen Namen wird dadurch nicht aktualisiert. Original `com.openai.chatgpt` und seine Daten werden nicht ersetzt.

Falls der Erstimport der Repository-Quelle wegen eines noch fehlenden Stable-Releases scheitert, unter **Sources → + → Remote** diese direkt an den Entwicklungszweig gebundene Metadaten-URL verwenden:

`https://raw.githubusercontent.com/sibbl/sibbl-chatgpt-patches/refs/heads/dev/patches-bundle.json`

Diese Quelle folgt immer den experimentellen dev-Releases. Alternativ die `.mpp` vom neuesten [Prerelease](https://github.com/sibbl/sibbl-chatgpt-patches/releases) als lokale Quelle importieren und im Expert-Modus die bereitgestellte APKM auswählen. Der Repository-Add-source-Link bleibt der reguläre Weg mit getrenntem Prerelease-Schalter.

Beide Apps beanspruchen weiterhin den ursprünglichen Login-Callback. Wenn Android eine Appauswahl zeigt, den **Klon** wählen. Callback-Routing, Browser/Auth Tab, Integritätsprüfung und vollständiger Login sind noch auf dem Gerät zu prüfen. Bitte nur Fehlerphase (vor Browser / im Browser / nach Rückkehr), Android-/Browser-Version und Fehlermeldung melden — keine Auth-URLs mit Parametern, Tokens oder Rohlogs veröffentlichen.

## Was geändert wird

- Clone-Manifestlogik auf Basis des offiziellen **Clone app** aus Morphe Patches **v1.45.0**.
- Custom Permissions und installierte Provider-Authorities werden getrennt benannt. Referenzen an geschützten Komponenten werden ebenfalls angepasst; fremde Provider unter `<queries>` bleiben unverändert.
- Genau zwei Rückgabewerte von `getPackageName()` im analysierten OAuth-Konfigurationsaufbau werden durch `com.openai.chatgpt` ersetzt. Ein dritter Aufruf für die tatsächliche Appidentität bleibt dynamisch.
- Exakte Versions- und Bytecode-Guards brechen bei abweichendem Eingang ab.

Der optionale Patch **Clone ChatGPT (experimental baseline)** allein dient nur dem Vergleich und enthält den Callback-Fix nicht.

<!-- PATCHES_START -->
> **[v1.0.0-dev.1](https://github.com/sibbl/sibbl-chatgpt-patches/releases/tag/v1.0.0-dev.1)**&nbsp;&nbsp;•&nbsp;&nbsp;`dev`&nbsp;&nbsp;•&nbsp;&nbsp;2 patches total
<details open>
<summary>📦 ChatGPT&nbsp;&nbsp;•&nbsp;&nbsp;2 patches</summary>
<br>

**🎯 Supported versions:**

| 🧪&nbsp;1.2026.265 |
| :---: |
| APK statically tested; installation and login remain unverified. |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Clone ChatGPT (experimental baseline)](#clone-chatgpt-experimental-baseline) | Separate package with mandatory permission and provider renaming. Authentication is unresolved: this patch does not fix invalid auth request. Do not combine with the universal Clone app patch. | • Package name<br>• Update permissions<br>• Update providers |
| [Preserve ChatGPT login callback (experimental)](#preserve-chatgpt-login-callback-experimental) | Keeps the original OAuth redirect URI when cloning. Includes the clone baseline. Static APK tests only: login and server acceptance are unverified; callback selection may still be needed. |  |

</details>

<!-- PATCHES_END -->

## Unterstützter Analyse- und Teststand

| Bestandteil | Version / Status |
|---|---|
| ChatGPT | 1.2026.265, Code 2626541; ausschließlich dieser Eingang |
| Morphe Manager | 1.33.0 als aktueller Stable-Stand geprüft |
| Offizielle Patches / Patcher / Buildplugin | 1.45.0 / 1.15.0 / 1.3.4 |
| Tests | Synthetische Manifesttests und lokaler statischer APK-Integrationstest |
| Gerät / Login | Nicht installiert, nicht gestartet, Login nicht bestätigt |

[Analyse und Belegstellen](docs/auth-analysis.md) · [Herkunft und Prüfsummen](docs/provenance.md) · [Build und Veröffentlichung](docs/development.md)

## Lokal bauen

Java 21 und bestehende GitHub-Packages-Leseberechtigung gemäß [Morphe-Dokumentation](https://github.com/MorpheApp/morphe-documentation) verwenden. Zugangsdaten nur im Benutzerprofil oder Prozess, nie im Repository speichern.

```sh
./gradlew :patches:test :patches:buildAndroid
```

Ergebnis: `patches/build/libs/patches-*.mpp`. Ein `.mpp` enthält Patches, keine ChatGPT-APK. Ohne `CHATGPT_TEST_APK` überspringt der Build den lokalen APK-Test; CI verwendet nur freie synthetische Fixtures.

## Lizenz und Herkunft

GPL-3.0 mit den in [NOTICE](NOTICE) erhaltenen Hinweisen. Basierend auf [Morphe Patches](https://github.com/MorpheApp/morphe-patches) und dem [offiziellen Template](https://github.com/MorpheApp/morphe-patches-template), ihrerseits mit ReVanced-Herkunft. Originalhinweise sind erhalten; Änderungen von sibbl sind markiert. OpenAI-Appdateien und dekompilierter Appcode gehören nicht zu diesem Repository und werden nicht mitgeliefert.
