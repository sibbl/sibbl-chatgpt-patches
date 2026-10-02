# Statische Auth-Diagnose — 2026-10-02

## Ergebnis und Grenzen

Ein konkreter Clientfehler ist nachgewiesen: Nach einer reinen Paketumbenennung wird eine andere OAuth-Redirect-URI konstruiert, während der Manifestfilter die ursprüngliche URI verlangt. Der enthaltene Fix beseitigt genau diese Abweichung.

Die gemeldete Meldung „invalid auth request“ lässt sich **noch nicht eindeutig** einer Serverantwort oder lokalen Fehlerphase zuordnen. Es wurde kein Login ausgeführt, kein Konto geändert und keine Anfrage mit neuen OAuth-Grants erzeugt. Statische Tests beweisen nicht, dass der Server einen neu signierten Klon akzeptiert. Die genaue Meldung war nicht als Stringkonstante in den untersuchten DEX-Stringtabellen enthalten; das allein beweist keinen Serverursprung.

## Reproduzierbare Belegstellen

Alle Symbole beziehen sich auf die in `provenance.md` gehashte Base-APK. Obfuskationsnamen gelten nur für diesen Build. Diese Aufstellung beschreibt Verhalten; proprietärer dekompilierter Quelltext wird nicht verteilt.

| Ort | Beobachtung | Konsequenz |
|---|---|---|
| `classes3.dex`, `Li280;->invoke()Ljava/lang/Object;` | Zwei Context.getPackageName-Aufrufe liefern Scheme und Paketsegment der Redirect-URI; Konstanten `://auth.openai.com/android/` und `/callback` rahmen sie ein. | Original erzeugt `com.openai.chatgpt://auth.openai.com/android/com.openai.chatgpt/callback`; Standardklon erzeugt `app.sibbl.chatgpt.private://auth.openai.com/android/app.sibbl.chatgpt.private/callback`. |
| Dasselbe Verfahren | Dritter getPackageName-Aufruf fließt separat in die Appkonfiguration. | Dieser wird ausdrücklich nicht geändert. |
| `classes.dex`, `Ll690;->b(...)` | Übernimmt Konfigurationsfeld `v6k0.c` als `redirect_uri`; generiert PKCE S256, state und nonce. | Rename verändert die an Auth übergebene URI; die Schutzmechanismen bleiben erhalten. |
| Manifest, `com.openai.feature.auth.impl.web.WebRedirectActivity` | Scheme `com.openai.chatgpt`, Pfadpräfix `/android/com.openai.chatgpt/callback`; u. a. Host `auth.openai.com`. | Unveränderte Filter passen nicht zur umbenannten URI. |
| WebRedirectActivity.onCreate | Leitet die erhaltene URI per explizitem Intent an die eigene WebAuthenticationActivity weiter. | Bei richtig gewähltem Klon bleibt der nächste Schritt in dessen Prozess. |
| `WebAuthenticationActivity` und Featurekonfiguration `la6` | Sowohl Custom Tabs als auch ein per Flag steuerbarer Auth-Tab-Pfad vorhanden. | Welcher Pfad auf dem Nutzergerät aktiv ist, ist nicht allein aus der APK bestimmbar. |
| `Ll690;`, Featureflag `ha6.l` | `app_hash` basiert auf Paketname und Signing-Zertifikat; Flag heißt `use_sms_retriever_for_otp_codes`. | Dieser Hash ist ein Hinweis auf SMS-Retriever-Zuordnung und kein ausreichender Beleg für eine Integritätssperre. Unverändert. |
| `Lehu0;`, `Lwxd0;` | Preauth-Integrity-Cookie-Pfad, Fehlerklassifikation `PlayIntegrityCheckFailed`, Backend-Rejection für 400/401/403 vorhanden. | Zusätzliche Integritätsablehnung ist möglich, aber für den beobachteten Fehler nicht bewiesen. Unverändert. |

DEX-Offsets im originalen i280.invoke (Byteoffset relativ zum Instruktionsbeginn): getPackageName bei `0x2c`, `0x34`, `0xde`; Domainkonstante `0x4c`; Callback-Suffix `0x5c`. Die tatsächliche DEX-Reihenfolge wurde zusätzlich zur Java-Darstellung geprüft. Der Patch überschreibt nur die ersten beiden nachfolgenden `move-result-object`-Instruktionen mit dem Originalpaketnamen.

## Warum nicht einfach das Callback-Scheme umbenennen?

Eine neue URI müsste auch vom Auth-Server akzeptiert werden. Eine solche Registrierung oder Freigabe liegt nicht vor. Der Patch behält deshalb die URI bei, die bereits von der unveränderten Original-App erzeugt wird. Das setzt weder Serverkonfiguration noch Tokenprüfung außer Kraft.

Original und Klon besitzen damit überlappende Callback-Intentfilter. Ein Android-Auswahldialog oder die Rückleitung an die falsche App bleibt möglich. Das lässt sich ohne Geräte-/Browserbeobachtung nicht als gelöst melden. state/PKCE dürfen nicht zur Kompensation abgeschaltet werden.

Die [offizielle App-Link-Zuordnung](https://chatgpt.com/.well-known/assetlinks.json) bindet das Originalpaket an Signaturzertifikate. Ein umbenannter und neu signierter Klon erbt diese HTTPS-Verifikation nicht. Das ist ein Routinghinweis; der hier analysierte Hauptcallback verwendet ein Custom Scheme. Die App-Link-Zuordnung beweist daher nicht die Ursache der Fehlermeldung.

## Zusätzliche konkrete Manifestkorrekturen

Die generische Upstreamlogik ändert deklarierte Permissions plus uses-permission. In dieser APK schützt jedoch `android:permission` den exportierten ChatGptAuthTokenService. Die lokale Anpassung benennt solche Referenzen konsistent um, damit sie weiterhin zur Permission des Klons gehören.

Upstream iteriert über alle provider-Elemente einschließlich externer Abfragen in `<queries>`. In dieser APK existieren solche Store-Abfragen. Die Anpassung benennt ausschließlich unter `<application>` installierte Provider um. Keine Nutzertokens werden gelesen und der Service wird nicht aufgerufen.

## Validierung

- Paket, Version, SHA256 und originale APK-Signatur lokal geprüft.
- Vier synthetische Tests: Deklarationen/Referenzen/Callback-Erhalt, Provider-Ressourcen, Versions-/Optionsguards, Ablehnung bereits geklonter Eingaben.
- Opt-in-Integrationstest auf der exakt gehashten APK: tatsächliche Patches einschließlich Finalizer ausgeführt; Ressourcen neu kompiliert; erzeugte DEX-Dateien erneut gelesen und nur die zwei festen Callback-Paketwerte sowie der weiter dynamische dritte Paketaufruf geprüft.
- Vollständige JADX-Dekompilation lief in ein Speicherlimit. Die benötigten Auth-Konfigurationsklassen wurden gezielt separat dekompiliert und durch DEX-Referenzen/Instruktionsreihenfolge ergänzt. Es wird keine vollständige Codeprüfung behauptet.
- Offen: fertiges APKM-Merging und Signieren im Manager, Installation neben Original, Browser-/Callback-Routing und Login auf dem Gerät. Keine APK wurde von der Entwicklung installiert oder gestartet.
