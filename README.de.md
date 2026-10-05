# SCM Analyzer

**Sprachen:** [English](README.md) · [Русский](README.ru.md) · **Deutsch**

Eine Android-App, die lokale oder von GitHub geklonte Repositories analysiert, ein tiefes
architektonisches Verständnis der Codebasis aufbaut und einen interaktiven, einklappbaren
HTML-Bericht mit Multi-LLM-Sicherheits- und Qualitätsbewertung erzeugt.

---

## Funktionen

- **Repository-Eingabe**
  - Lokalen Ordner über das Storage Access Framework (SAF) öffnen.
  - Öffentliches GitHub-Repository klonen (Zipball; die Git-Historie wird für die Analyse nicht benötigt).
  - Ein mitgeliefertes Beispiel-Repository für einen sofortigen End-to-End-Test.
- **Rekursive Analyse** von Kotlin, Java, JavaScript/TypeScript, Python, C/C++, Go, Rust, C#, PHP,
  Ruby, Shell sowie Markup/Konfiguration (HTML, CSS, XML, JSON, YAML, TOML, SQL, Dockerfile …).
- **Nur echter, ausführbarer Code wird analysiert.** README/`*.md`/`*.txt`/`*.rst` und andere
  Dokumentation werden übersprungen; reine Kommentar- und Leerzeilenbereiche werden ignoriert.
- **Multi-LLM-Prüfung** mit vier eingebauten Anbietern und beliebig vielen eigenen
  OpenAI-kompatiblen:
  | Anbieter | Basis-URL | Hinweis |
  |---|---|---|
  | DevHorizon (primär) | `https://llm.devhorizon.online/v1` | Kein API-Schlüssel nötig |
  | Ollama Cloud | `https://ollama.com/v1` | API-Schlüssel / OAuth |
  | OpenCode Zen | `https://opencode.ai/zen/v1` | Standard-Gratismodell |
  | OpenCode Go | `https://opencode.ai/zen/go/v1` | Eigenes Modell |
- **Express-Bericht (Ensemble).** Führt denselben Code nacheinander durch alle konfigurierten
  Anbieter, erstellt einen Bericht pro Modell und vergleicht dann alle Urteile zu einem
  **Konsens-Superbericht** (Mehrheitsentscheid, Eskalation zum strengsten Urteil, „disputed"-Markierung).
- **Interaktiver HTML-Bericht**
  - Vier Spalten: Name/Beschreibung · Kategorie/Technologie/Typ · Exakter Code & Zeilenverweis ·
    Qualität & Sicherheit.
  - Einklappbare Zeilen; jeder Block, der breiter als ¼ des Bildschirms ist, wird automatisch eingeklappt.
  - Urteil-Badges: ✅ best practice · ⚠️ Warnung · 🛡️ Schwachstelle · ℹ️ Info.
  - Sofortsuche, Filter nach Typ/Urteil, Hell-/Dunkelmodus, „alles aus-/einklappen".
  - Export, Teilen oder im Browser öffnen.

## Wie es funktioniert

```
Aufnahme ─▶ Filter ─▶ Spracherkennung ─▶ Symbole parsen ─▶ jede Codezeile zuordnen ─▶ Bewerten ─▶ Bericht
 (SAF/                                      (Regex/Heuristik                 (statische Regeln
  GitHub)                                    je Sprachfamilie)                + LLM-Batches)
```

- **Reproduzierbare Abdeckungsmetrik:** Jede Zeile *echten Codes* wird einer Berichtzeile zugeordnet;
  der Bericht zeigt `zugeordnet / gesamte Codezeilen`. Erkannte Symbole (Klassen, Funktionen, Methoden,
  Variablen, UI-Komponenten, Trigger) erhalten eigene Zeilen; der restliche Code wird zu
  „Code-Block"-Zeilen gruppiert.
- **Bewertung:** Ein lokales Regelwerk (fest codierte Geheimnisse, SQL-/Command-Injection, `eval`,
  unverschlüsseltes HTTP, schwache Krypto, deaktivierte TLS-Prüfung, XSS-Sinks, leeres `catch`,
  TODO …) liefert eine Basis; das LLM verfeinert sie. Ergebnisse werden per Inhalts-Hash
  zwischengespeichert, sodass Wiederholungen und Anbieterwechsel keine Tokens erneut verbrauchen.

## Voraussetzungen

- Android 8.0+ (minSdk 26), getestet auf Android 13 (API 33).
- Android SDK Platform 37 & Build-Tools 37 zum Bauen.
- JDK 17.
- AGP 9.1.1 mit eingebautem Kotlin (Kotlin 2.2.10), Gradle 9.3.1, Jetpack Compose Material 3.

## Bauen & Installieren

```bash
# local.properties auf das SDK zeigen lassen, z. B.
# sdk.dir=C\:\\Android\\Sdk

./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Projektstruktur

```
app/src/main/java/online/devhorizon/scm/
  domain/model/     CodeElement, Badge, AnalysisResult, AnalysisOptions
  domain/ingest/    LanguageDetector, RepoFilter, RepoSource (File/SAF/Cached), GitHubRepo
  domain/parse/     BraceAnalyzer, PythonAnalyzer, MarkupAnalyzer, CodeIndexer (Zeilenabdeckung)
  domain/assess/    StaticRules, LlmAssessor (Batching + Cache), AssessmentCache
  domain/express/   ExpressPipeline, Consensus
  domain/           AnalysisPipeline, ResultAssembler, Progress
  data/llm/         ProviderConfig, ProviderStore (EncryptedSharedPreferences), OpenAiCompatClient
  report/           HtmlReportBuilder, SuperReportBuilder, ReportStorage
  ui/               MainViewModel + Screens (Home, Analyze, Providers, Report)
app/src/main/assets/  report_template.html, super_report_template.html, sample_repo/
```

## Datenschutz & Sicherheit

- API-Schlüssel werden in verschlüsselten Shared Preferences (Android Keystore) gespeichert und
  niemals in Berichte oder Workflows geschrieben.
- In Berichte eingebetteter Repository-Text wird HTML-escaped, sodass analysierter Code keine
  Skripte einschleusen kann.
- Das erzeugte HTML ist vollständig eigenständig; JavaScript dient nur der Bericht-UI.

## Einschränkungen

- Heuristische Parser nähern die Symbolerkennung an; es sind keine vollständigen AST-Parser.
- Die LLM-Prüfung ist durch ein Element-Budget pro Lauf begrenzt und benötigt Netzwerkzugriff.
- Private GitHub-Repositories werden nicht unterstützt (nur öffentlicher Zipball).

## Änderungsverlauf

Siehe [change-history.txt](change-history.txt).

## Lizenz

Es ist noch keine Lizenzdatei enthalten. Fügen Sie eine hinzu, bevor Sie das Projekt verbreiten.
