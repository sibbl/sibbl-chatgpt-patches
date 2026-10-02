# Statische Auth-Diagnose — 2026-10-02

## Ergebnis und Grenzen

Ein konkreter Clientfehler ist nachgewiesen: Nach einer reinen Paketumbenennung wird eine andere OAuth-Redirect-URI konstruiert, während der Manifestfilter die ursprüngliche URI verlangt. Der enthaltene Fix beseitigt genau diese Abweichung.

Die gemeldete Meldung „invalid auth request“ lässt sich **noch nicht eindeutig** einer Serverantwort oder lokalen Fehlerphase zuordnen. Es wurde kein Login ausgeführt, kein Konto geändert und keine Anfrage mit neuen OAuth-Grants erzeugt. Statische Tests beweisen nicht, dass der Server einen neu signierten Klon akzeptiert. Die genaue Meldung war nicht als Stringkonstante in den untersuchten DEX-Stringtabellen enthalten; das allein beweist keinen Serverursprung.

## Reproduzierbare Belegstellen

Alle Symbole beziehen sich auf die in `provenance.md` gehashte Base-APK. Obfuskationsnamen gelten nur für diesen Build. Diese Aufstellung beschreibt Verhalten; proprietärer dekompilierter Quelltext wird nicht verteilt.

| Ort | Beobachtung | Konsequenz |
|---|---|---|
| `classes3.dex`, `Li280;->invoke()Ljava/lang/Object;` | Zwei Context.getPackageName-Aufrufe liefern Scheme und Paketsegment der Redirect-URI; Konstanten `://auth.openai.com/android/` und `/callback` rahmen sie ein. | Original erzeugt `com.openai.chatgpt://auth.openai.com/android/com.openai.chatgpt/callback`; Standardklon ohne Callback-Fix erzeugt `com.openai.chatgpt.clone://auth.openai.com/android/com.openai.chatgpt.clone/callback`. |
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
- Synthetische Tests für Manifestreferenzen, Provider-Ressourcen, Versions-/Optionsguards, informative Fehlermeldungen und Unicode/XML-/Launcher-Namen.
- Opt-in-Integrationstest auf der exakt gehashten APK: tatsächliche Patches einschließlich Finalizer ausgeführt; Ressourcen neu kompiliert; erzeugte DEX-Dateien erneut gelesen und nur die zwei festen Callback-Paketwerte sowie der weiter dynamische dritte Paketaufruf geprüft.
- Vollständige JADX-Dekompilation lief in ein Speicherlimit. Die benötigten Auth-Konfigurationsklassen wurden gezielt separat dekompiliert und durch DEX-Referenzen/Instruktionsreihenfolge ergänzt. Es wird keine vollständige Codeprüfung behauptet.
- Nutzer bestätigt inzwischen den erfolgreichen Patchbuild in Morphe mit der unterstützten Eingabe. Browser-/Callback-Routing, tatsächliche Installation neben Original und Login bleiben nicht durch die Entwicklung bestätigt. Keine APK wurde von der Entwicklung installiert oder gestartet.

## Gerätebefund: „Incorrect email address or password“

Der Nutzer meldet nach erfolgreichem Patchen diese neue Loginmeldung. Das ist ein getrennt zu untersuchender Befund und kein bestätigter Login. Die genaue Fehlerphase und der ursprünglich genutzte Anmeldeweg sind noch offen; es wird weder ein falsches Passwort noch eine bestimmte Serverprüfung als Ursache behauptet.

Erneute statische Prüfung: `i280.invoke` übernimmt Auth-Konfigurationswerte unverändert aus `ugc` in `v6k0`; die zwei ersetzten Rückgaben gehen allein in dessen Redirect-Feld. `l690.b` liest `client_id` aus `v6k0.a` und `redirect_uri` aus `v6k0.c`, erzeugt unverändert PKCE S256/state/nonce und übernimmt unverändert die übrigen Authparameter. Der Patch wählt keine Passwort- oder Social-Loginroute aus, schreibt keine Zugangsdaten und verändert keine Cookie-/Credential-Storage-Funktionen. Der dritte Paketaufruf, SMS-app_hash und Integritätspfade bleiben dynamisch beziehungsweise unverändert. Neue Appdaten sind durch den neuen Paketnamen getrennt; bestehende Kontotokens werden nicht übertragen. Das ist eine abgegrenzte Quellprüfung, keine vollständige Prüfung aller Browser-/Serverzustände.

Für die Einordnung genügen nicht geheime Angaben: verwendete Methode (E-Mail/Passwort oder Google/Apple/Microsoft), ob dieselbe Methode in Originalapp beziehungsweise normalem Browser funktioniert, und Fehlerphase (vor Browser, im Browser, nach Rückkehr). Die [offizielle OpenAI-Hilfe zur Anmeldemethode](https://help.openai.com/en/articles/4936824-can-i-change-how-i-log-into-my-account-authentication-method) beschreibt Unterschiede zwischen Passwort- und Social-Anmeldung; die [Login-Hilfe](https://help.openai.com/en/articles/7426629-why-cant-i-log-in-to-chatgpt) behandelt den ursprünglichen Anmeldeweg und Browserkontext. Beides sind mögliche Diagnoseansätze, keine Belege für diesen Nutzerfall. Keine Passwörter, Tokens, vollständigen Auth-URLs oder Rohlogs teilen; kein Passwortreset oder Kontoänderung wird angefordert.
