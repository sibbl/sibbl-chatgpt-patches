# Auth-Differentialanalyse — 2026-10-02

## Ergebnis

Der Nutzer bestätigt einen erfolgreichen frischen E-Mail/Passwort-Login im privaten Browser. Der App-/Klon-Login meldet weiterhin „Incorrect email address or password“. Damit ist ein pauschaler Verweis auf falsche Zugangsdaten nicht begründet. Ob auf dem Gerät tatsächlich ein mit dev.4 gebauter Klon läuft und ob die Meldung im Browser, in einer nativen Ansicht oder nach Rückkehr erscheint, ist noch nicht festgestellt.

Die vertiefte Offline-Prüfung findet keinen weiteren nachgewiesenen Fehler in Redirect-URI oder Auth-Tab-Scheme. Es gibt paketabhängige Unterschiede in nativen HTTP-Metadaten. Deren Ablehnung durch den Server ist nicht belegt. Deshalb wird hier kein weiterer „Login-Fix“ veröffentlicht und keine Sicherheitsprüfung verändert. Die veröffentlichten Patches bleiben dev.4.

## Grundlage und reproduzierbarer Test

Eingang bleibt die in [provenance.md](provenance.md) gehashte Original-Base-APK 1.2026.265 / 2626541. Keine installierte App und keine Nutzersitzung wurden ausgelesen. Die Quelle enthält weder APK noch dekompilierten Appcode.

Der lokale `RealApkTest` baut die tatsächliche Patchausgabe. `AuthDifferential.kt` vergleicht 29 ausgewählte Klassen aus Original und erzeugten DEX-Dateien. Jede Klasse wird einzeln in einen kanonischen DEX-Pool geschrieben, damit unterschiedliche String-/Methodenindizes keinen falschen Unterschied erzeugen. **Alle 28 Klassen außer dem gezielt geänderten `i280` sind byteidentisch in dieser Darstellung.** Dazu gehören Requestaufbau, Auth-API, Browseraktivitäten, Auth-Tab-/Custom-Tab-Dispatcher, native Headerkonfiguration, Telemetrie und die untersuchten Integritätsadapter.

Zusätzlich verfolgt ein begrenzter Offline-Interpreter die tatsächlichen String- und Registeroperationen von `i280.invoke`. Er führt keine Android- oder Appfunktionen aus und erzeugt keine Netzwerkanfrage. Er prüft Original, Klon ohne Callback-Fix, dev.4 und einen frei gewählten anderen Klon-Paketnamen. Die Auth-Tab-Instruktionskette wird separat bis zum Argument von `Intent.putExtra` geprüft.

Alle zehn lokalen Tests bestanden, einschließlich des APK-Tests. CI kann ohne `CHATGPT_TEST_APK` nur die freien Tests ausführen und überspringt den APK-Test. Diese Prüfung simuliert weder den Android-Resolver noch Browsercookies oder Serverentscheidungen.

## Konfiguration und Callback

| Wert/Pfad | Original | Klon ohne Callback-Fix | dev.4 |
|---|---|---|---|
| Installiertes Paket / dritter Paketaufruf | `com.openai.chatgpt` | gewählter Klonname | gewählter Klonname |
| Redirect-Scheme und Paketsegment | Originalpaket | Klonname, passt nicht zum bisherigen Manifest | Originalpaket, passt zum Manifest |
| Vollständige tokenlose Callback-Basis | `com.openai.chatgpt://auth.openai.com/android/com.openai.chatgpt/callback` | Klonname an beiden Paketpositionen | wie Original |
| Auth-Client-ID und Google-Client-Konfiguration | Appkonstanten aus `ugc` | unverändert | unverändert |
| Browser-Autorisierungsendpunkt | `https://auth.openai.com/api/accounts/authorize` | unverändert | unverändert |
| PKCE S256, state, nonce | pro Sitzung erzeugt | unverändert | unverändert |
| Optionaler SMS-`app_hash` | aus tatsächlichem Paket + tatsächlichem Zertifikat | klonspezifisch | klonspezifisch |

`l690.b` übernimmt `v6k0.c` in `redirect_uri`. `ofu0.a` übergibt die daraus gebaute Autorisierungs-URI an den ausgewählten Browser. Die untersuchten Browser-Startpfade setzen keinen `OAI-Package-Name`-Header auf diese Browsernavigation. Der native HTTP-Client und der Browsertransport sind unterschiedliche Pfade.

**Auth Tab:** `amq0.invoke` liest `ofu0.a → w6k0.y → v6k0.c`, ruft `Uri.parse(...).getScheme()` auf und verwendet genau dieses Ergebnis für `androidx.browser.auth.extra.REDIRECT_SCHEME`. Der neue Test prüft auch die Registerzuordnung. `WebAuthenticationActivity.v` verwendet den Auth-Tab-ActivityResult-Vertrag. Dieser Pfad ist kein Beleg für implizites Android-Missrouting zwischen Original und Klon.

**Custom Tabs / Fallback:** Der Browser erhält dieselbe korrigierte URI. Für einen anschließenden Custom-Scheme-Intent beanspruchen Original und Klon weiterhin denselben Manifestfilter. `WebRedirectActivity` leitet einen angekommenen Intent ausdrücklich an die eigene `WebAuthenticationActivity` weiter. Welche App den ersten Intent bekommt, bleibt eine Gerätebeobachtung. `ofu0.b` prüft Scheme, Host, Pfad und state weiterhin. Bei falschem Callback nennt der Code andere konkrete Fehler als den beobachteten Passworttext.

## Sämtliche direkten DEX-Konsumenten des dritten Paketwerts

Eine Referenzsuche über alle acht DEX-Dateien findet für `w6k0.z` neben Konstruktor/equals/hashCode diese drei funktionalen Leser:

1. **`o8n0.intercept`: Telemetrie.** Schreibt `OAI-Package-Name` aus `w6k0.z`. Der vorgeschaltete Test `wud0.o` lässt diesen Zweig nur für einen Pfad mit Endung `/v1/log_event` zu, zusätzlich mit `/ces/` im Pfad oder lokalem Host aus `x6k0.a` (`localhost`, `127.0.0.1`, `10.0.2.2`). Die DEX-Verzweigungen wurden zusätzlich zur Java-Darstellung geprüft. Das ist kein Passwort-/Token-Endpunkt. `m8n0.d/e` bindet diesen Interceptor an die Telemetrie-Konfiguration.
2. **`zh7.invoke`, durch `f280.invoke` mit Discriminator 29 registriert: Ktor-DefaultRequest.** Setzt Basis-URL, User-Agent und `OAI-Package-Name` für den nativen HTTP-Client. Die Basis kommt aus `w6k0.b` (ursprünglich Android-Backend-API); einzelne Anfragen können einen anderen Endpunkt setzen. Dies ist der relevante Unterschied für allgemeine native API-Aufrufe. Auch die native Auth-API `u56.e` baut einen Aufruf an `https://auth.openai.com/oauth/token` und führt ihn über `ii7.h` aus. Das ist statische Pfadzuordnung, kein beobachteter Wire-Request: aufrufabhängige Konfiguration und Headerüberschreibungen auf dem Gerät wurden nicht mitgeschnitten.
3. **`xhv.c`: Ledger-/Plaid-Kontoverknüpfung.** Reicht den tatsächlichen Paketnamen in die native Verknüpfungsanfrage weiter. Kein E-Mail/Passwort-Login. Eine pauschale Änderung des dritten Rückgabewerts würde auch diesen unabhängigen Pfad ändern.

Im Original ist der paketabhängige Headerwert `com.openai.chatgpt`, im Klon entspricht er dessen Paketnamen. App-Version, Buildnummer und der feste App-Bezeichner `w6k0.l` bleiben hingegen wie im Original. Gerät-ID und privater Zustand können in einer separaten Installation neu entstehen; reale Werte wurden nicht erhoben.

Zwei weitere direkte Vorkommen des Headernamens liegen in `aq1` und `com.openai.valdi.integrity.b`. Sie verwenden einen eigenen festen Bezeichner und lesen **nicht** `w6k0.z`. Sie bleiben unverändert. Ebenso bleiben echte Zertifikate, Integritätsnachweise, DPoP und das paket-/zertifikatabhängige SMS-Verfahren unverändert. Ein globaler Paketwert-Ersatz würde somit weder sämtliche Header vereinheitlichen noch einen belegten Authfehler gezielt beheben.

## Woher kann der Fehlertext kommen?

Die exakte englische Meldung wurde in allen acht DEX-Dateien und der Ressourcen-Tabelle nicht als ASCII-/UTF-16LE-Text gefunden. Das bestimmt die Anzeigequelle noch nicht:

- Ein Browser kann den Text aus einer Webantwort anzeigen.
- `ofu0.b` übernimmt bei gültigem Callback und passendem state einen externen `error_description`-Text in `AuthError.WebAuthFailed`. Dadurch kann ein nicht fest in der APK enthaltenes Serverwortlaut auch nach Rückkehr in die native App gelangen.
- Die APK enthält außerdem native Onboarding-/Passwortpfade und native API-Fehlerverarbeitung. Deren Existenz beweist nicht, dass sie bei diesem Versuch verwendet wurden.

Ohne Fehlerphase, aktuelle Paket-/Patchidentität und einen nicht geheimen Fehlercode lässt sich daraus keine konkrete Server- oder Integritätsursache ableiten.

## Entscheidung und kleinste nächste Beobachtung

Es gibt aktuell **keinen ausreichend belegten weiteren Clientfix**. Den Header auf das Original zurückzusetzen wäre eine neue Kompatibilitätshypothese, keine Korrektur einer nachgewiesenen falschen Redirect-Anfrage. Aus der APK allein ist auch nicht ersichtlich, welche Bedeutung der Server diesem Identitätsfeld gibt. Daher wird kein solcher Kandidat als „Passwortfix“ eingebaut oder veröffentlicht.

Als nächstes genügen zunächst ohne erneuten Login: der tatsächlich installierte Klon-Paketname und die Patchversion sowie die Beschreibung, ob der Fehler im Browser, in einer nativen Anmeldemaske oder erst nach Rückkehr erscheint. Nach Wiederherstellung der bereits angebotenen ADB-Verbindung können passive Callback-Handler und minimale Activity-Komponenten geprüft werden. Erst danach ist höchstens ein vom Nutzer ausgeführter, gezielt beobachteter Versuch sinnvoll. Vollständige URLs, Passwörter, Tokens, Cookies, PKCE/state-Werte, UI-Dumps und unbeschränktes Logcat sind dafür nicht vorgesehen.

Der angebotene ADB-Endpunkt war bei der ersten Verbindung mit „No route to host“ unerreichbar. Es wurden keine weiteren ADB-Versuche unternommen; der Nutzer prüft die Erreichbarkeit.
