# SCM Analyzer

**Языки:** [English](README.md) · **Русский** · [Deutsch](README.de.md)

[![Android CI](https://github.com/sashok53011/scm-analyzer/actions/workflows/android.yml/badge.svg)](https://github.com/sashok53011/scm-analyzer/actions/workflows/android.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
![Platform](https://img.shields.io/badge/platform-Android-3DDC84)
![minSdk](https://img.shields.io/badge/minSdk-26-blue)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF)

Android-приложение, которое анализирует локальные или склонированные с GitHub репозитории,
строит глубокое архитектурное понимание кодовой базы и формирует интерактивный сворачиваемый
HTML-отчёт с мульти-LLM оценкой качества и безопасности.

---

## Возможности

- **Источник репозитория**
  - Открытие локальной папки через Storage Access Framework (SAF).
  - Клонирование публичного репозитория GitHub (zipball; история git для анализа не нужна).
  - Встроенный демонстрационный репозиторий для быстрой проверки всего конвейера.
- **Рекурсивный анализ** Kotlin, Java, JavaScript/TypeScript, Python, C/C++, Go, Rust, C#, PHP,
  Ruby, Shell, а также разметки/конфигов (HTML, CSS, XML, JSON, YAML, TOML, SQL, Dockerfile…).
- **Анализируется только реальный исполняемый код.** README/`*.md`/`*.txt`/`*.rst` и прочая
  документация пропускаются; чистые комментарии и пустые строки игнорируются.
- **Мульти-LLM проверка**: четыре встроенных провайдера и любые свои OpenAI-совместимые:
  | Провайдер | Base URL | Примечание |
  |---|---|---|
  | DevHorizon (основной) | `https://llm.devhorizon.online/v1` | API-ключ не нужен |
  | Ollama Cloud | `https://ollama.com/v1` | API-ключ / OAuth |
  | OpenCode Zen | `https://opencode.ai/zen/v1` | Бесплатная модель по умолчанию |
  | OpenCode Go | `https://opencode.ai/zen/go/v1` | Своя модель |
- **Express-отчёт (ансамбль).** Прогоняет один и тот же код по очереди через все настроенные
  провайдеры, делает отдельный отчёт по каждой модели, затем сравнивает вердикты в **общий
  супер-отчёт** (голосование большинством, эскалация к самой строгой оценке, пометка «disputed»).
- **Интерактивный HTML-отчёт**
  - Четыре колонки: Имя/Описание · Категория/Технология/Тип · Точный код и ссылка на строки ·
    Качество и безопасность.
  - Сворачиваемые строки; любой блок шире ¼ экрана автоматически сворачивается.
  - Значки-вердикты: ✅ оптимально · ⚠️ предупреждение · 🛡️ уязвимость · ℹ️ инфо.
  - Мгновенный поиск, фильтры по типу/вердикту, тёмная/светлая тема, «развернуть/свернуть всё».
  - Экспорт, отправка и открытие в браузере.
- **Локализованный интерфейс** на английском, русском и немецком с переключателем языка
  в приложении; HTML-отчёт и вердикты LLM следуют выбранному языку.

## Как это работает

```
Приём ─▶ Фильтр ─▶ Определение языка ─▶ Разбор символов ─▶ Покрытие каждой строки кода ─▶ Оценка ─▶ Отчёт
(SAF/GitHub)                                (regex/эвристики                    (статические правила
                                             по семействам языков)               + LLM-батчи)
```

- **Воспроизводимая метрика покрытия:** каждая строка *реального кода* привязана к строке отчёта,
  а отчёт показывает `покрыто / всего строк кода`. Найденным символам (классы, функции, методы,
  переменные, UI-компоненты, триггеры) соответствует отдельная строка; остальной код группируется
  в строки-«code block».
- **Оценка:** локальный набор статических правил (захардкоженные секреты, SQL/command-инъекции,
  `eval`, HTTP без шифрования, слабая криптография, отключённая проверка TLS, XSS-стоки, пустой
  `catch`, TODO…) даёт базовую оценку; LLM её уточняет. Результаты кэшируются по хэшу содержимого —
  повторные прогоны и смена провайдера не тратят токены заново.

## Требования

- Android 8.0+ (minSdk 26), проверено на Android 13 (API 33).
- Android SDK Platform 37 и Build-Tools 37 для сборки.
- JDK 17.
- AGP 9.1.1 со встроенным Kotlin (Kotlin 2.2.10), Gradle 9.3.1, Jetpack Compose Material 3.

## Сборка и установка

```bash
# укажите путь к SDK в local.properties, например:
# sdk.dir=C\:\\Android\\Sdk

./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Структура проекта

```
app/src/main/java/online/devhorizon/scm/
  domain/model/     CodeElement, Badge, AnalysisResult, AnalysisOptions
  domain/ingest/    LanguageDetector, RepoFilter, RepoSource (File/SAF/Cached), GitHubRepo
  domain/parse/     BraceAnalyzer, PythonAnalyzer, MarkupAnalyzer, CodeIndexer (покрытие строк)
  domain/assess/    StaticRules, LlmAssessor (батчи + кэш), AssessmentCache
  domain/express/   ExpressPipeline, Consensus
  domain/           AnalysisPipeline, ResultAssembler, Progress
  data/llm/         ProviderConfig, ProviderStore (EncryptedSharedPreferences), OpenAiCompatClient
  report/           HtmlReportBuilder, SuperReportBuilder, ReportStorage
  ui/               MainViewModel + экраны (Home, Analyze, Providers, Report)
app/src/main/assets/  report_template.html, super_report_template.html, sample_repo/
```

## Приватность и безопасность

- API-ключи хранятся в зашифрованных настройках (Android Keystore) и никогда не попадают в
  отчёты или workflow.
- Текст репозитория, встроенный в отчёт, экранируется по HTML, поэтому анализируемый код не может
  внедрить скрипты.
- Сгенерированный HTML полностью автономен; JavaScript используется только для UI отчёта.

## Ограничения

- Эвристические парсеры приблизительно извлекают символы; это не полноценные AST-парсеры.
- LLM-проверка ограничена бюджетом элементов за прогон и требует доступа в сеть.
- Приватные репозитории GitHub не поддерживаются (только публичный zipball).

## История изменений

См. [change-history.txt](change-history.txt).

## Лицензия

Проект распространяется под [лицензией MIT](LICENSE).
