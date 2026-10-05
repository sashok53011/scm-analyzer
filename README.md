# SCM Analyzer

**Languages:** **English** · [Русский](README.ru.md) · [Deutsch](README.de.md)

[![Android CI](https://github.com/sashok53011/scm-analyzer/actions/workflows/android.yml/badge.svg)](https://github.com/sashok53011/scm-analyzer/actions/workflows/android.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
![Platform](https://img.shields.io/badge/platform-Android-3DDC84)
![minSdk](https://img.shields.io/badge/minSdk-26-blue)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF)

An Android app that analyzes local or GitHub-cloned repositories, builds a deep architectural
understanding of the codebase, and renders an interactive, collapsible HTML report with
multi-LLM security and quality review.

---

## Features

- **Repository input**
  - Open a local folder via the Storage Access Framework (SAF).
  - Clone a public GitHub repository (zipball; no git history needed for analysis).
  - A bundled sample repository for instant end-to-end testing.
- **Recursive analysis** of Kotlin, Java, JavaScript/TypeScript, Python, C/C++, Go, Rust, C#,
  PHP, Ruby, Shell, plus markup/config (HTML, CSS, XML, JSON, YAML, TOML, SQL, Dockerfile…).
- **Only real, executable code is analyzed.** README/`*.md`/`*.txt`/`*.rst` and other docs are
  skipped; pure comment and blank line ranges are ignored.
- **Multi-LLM review** with four built-in providers and unlimited custom OpenAI-compatible ones:
  | Provider | Base URL | Notes |
  |---|---|---|
  | DevHorizon (primary) | `https://llm.devhorizon.online/v1` | No API key required |
  | Ollama Cloud | `https://ollama.com/v1` | API key / OAuth |
  | OpenCode Zen | `https://opencode.ai/zen/v1` | Default free model |
  | OpenCode Go | `https://opencode.ai/zen/go/v1` | Custom model |
- **Express report (ensemble).** Runs the same code through every configured provider in turn,
  produces one report per model, then compares all verdicts into a **consensus super-report**
  (majority vote, escalation to the most severe verdict, "disputed" flags).
- **Interactive HTML report**
  - Four columns: Name/Description · Category/Technology/Type · Exact code & line reference ·
    Quality & Security.
  - Collapsible rows; any block wider than ¼ of the screen auto-collapses.
  - Verdict badges: ✅ best practice · ⚠️ warning · 🛡️ vulnerability · ℹ️ info.
  - Instant search, kind/verdict filters, dark/light theme, expand/collapse all.
  - Export to storage, share, or open in a browser.
- **Localized interface** in English, Russian and German, with an in-app language switcher;
  the HTML report and the LLM verdicts follow the selected language.

## How it works

```
Ingest ─▶ Filter ─▶ Language detect ─▶ Parse symbols ─▶ Map every code line ─▶ Assess ─▶ Report
 (SAF/                                     (regex/heuristic              (static rules
  GitHub)                                   per language family)          + LLM batches)
```

- **Reproducible coverage metric:** every line of *real code* is attached to a report row, and the
  report shows `mapped / total code lines`. Detected symbols (classes, functions, methods,
  variables, UI components, triggers) get their own rows; the remaining code becomes grouped
  "code block" rows.
- **Assessment:** a local static rule pack (hardcoded secrets, SQL/command injection, `eval`,
  cleartext HTTP, weak crypto, disabled TLS verification, XSS sinks, empty catch, TODO…) provides
  a baseline; the LLM refines it. Results are cached by content hash, so repeated runs and provider
  switches do not re-spend tokens.

## Requirements

- Android 8.0+ (minSdk 26), tested on Android 13 (API 33).
- Android SDK Platform 37 & Build-Tools 37 for building.
- JDK 17.
- AGP 9.1.1 with built-in Kotlin (Kotlin 2.2.10), Gradle 9.3.1, Jetpack Compose Material 3.

## Build & install

```bash
# point local.properties at your SDK, e.g.
# sdk.dir=C\:\\Android\\Sdk

./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Project structure

```
app/src/main/java/online/devhorizon/scm/
  domain/model/     CodeElement, Badge, AnalysisResult, AnalysisOptions
  domain/ingest/    LanguageDetector, RepoFilter, RepoSource (File/SAF/Cached), GitHubRepo
  domain/parse/     BraceAnalyzer, PythonAnalyzer, MarkupAnalyzer, CodeIndexer (line coverage)
  domain/assess/    StaticRules, LlmAssessor (batching + cache), AssessmentCache
  domain/express/   ExpressPipeline, Consensus
  domain/           AnalysisPipeline, ResultAssembler, Progress
  data/llm/         ProviderConfig, ProviderStore (EncryptedSharedPreferences), OpenAiCompatClient
  report/           HtmlReportBuilder, SuperReportBuilder, ReportStorage
  ui/               MainViewModel + screens (Home, Analyze, Providers, Report)
app/src/main/assets/  report_template.html, super_report_template.html, sample_repo/
```

## Privacy & security

- API keys are stored in encrypted shared preferences (Android Keystore) and never written into
  workflows or reports.
- Repository text embedded in reports is HTML-escaped, so analyzed code cannot inject scripts.
- The generated HTML is fully self-contained; JavaScript is only used for the UI of the report.

## Limitations

- Heuristic parsers approximate symbol extraction; they are not full AST parsers.
- LLM review is limited by a per-run element budget and needs network access.
- Private GitHub repositories are not supported (public zipball only).

## Change history

See [change-history.txt](change-history.txt).

## License

Released under the [MIT License](LICENSE).
