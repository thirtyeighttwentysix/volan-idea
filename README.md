# Volan Schema for IntelliJ IDEA

[![CI](https://github.com/thirtyeighttwentysix/volan-idea/actions/workflows/ci.yml/badge.svg)](https://github.com/thirtyeighttwentysix/volan-idea/actions/workflows/ci.yml)
[![Release](https://github.com/thirtyeighttwentysix/volan-idea/actions/workflows/release.yml/badge.svg)](https://github.com/thirtyeighttwentysix/volan-idea/actions/workflows/release.yml)

![Volan](assets/logo-mark.svg)

Отдельный плагин на Kotlin для файлов `*.volan`. Проект самостоятельно
собирается и не включён в Gradle-проект ORM.

## Возможности

- Подсветка строк, чисел, комментариев, ключевых слов, типов и атрибутов.
- `Ctrl+Space`: шаблоны блоков, свойства datasource/generator, скалярные типы,
  объявленные модели и enum, атрибуты, `@db.*`, аргументы связей, локальные
  и внешние поля, значения enum, функции default и referential actions.
- Семантические подсказки: скрываются повторные атрибуты и вторые первичные
  ключи, учитываются nullable/list, возможности SQLite и тип native-поля.
  Для связей предлагаются совместимые поля и первичные/уникальные ключи
  целевой модели; составные ключи дополняются в объявленном порядке.
- Шаблон `@relation` подставляет существующие внешние ключи, если их можно
  однозначно определить по имени и типу. Для стороны `[]` предлагается
  имя связи. `SetNull` скрывается, если известные внешние ключи обязательны.
- Типы сортируются с учётом имени поля (`email` → `String`, `createdAt` →
  `DateTime`) и типа ключа связанной модели. На новой строке доступны
  шаблоны `id`, `createdAt`, `updatedAt`; существующие поля не повторяются.
- Дополнение автоматически открывается после `@`, `@db.`, `:`, `[` и
  пробела после имени поля/`=`. Комментарии и строки не вызывают этот popup.
  Вставка повторно использует уже написанные скобки вместо дублирования.
- Ошибки синтаксиса и семантики, предупреждения и пояснения с исходными
  кодами Volan (`E00xx`, `E01xx`, `E02xx`). Проверяются также ключи,
  дубликаты, значения по умолчанию и обе стороны связей.
- `Ctrl+Q`: документация типов, атрибутов, свойств и объявлений;
  пользовательские `///`-комментарии отображаются в документации.
- `Ctrl+B` / Ctrl+Click: переход к модели/enum, значению enum и полям
  в `fields`, `references` и списках ограничений.
- `Ctrl+Alt+L`: каноническое форматирование всего файла через форматтер
  ORM, включая выравнивание колонок, комментарии и группы полей.
- Сворачивание блоков, сопоставление скобок, `Ctrl+/` для комментариев.
- Ваш логотип волана используется для файлов, списка дополнений и самого
  плагина. SVG-варианты предусмотрены для светлой и тёмной темы;
  исходное изображение хранится в `assets/logo-original.png`.

Плагин рассчитан на IDEA **2026.1.x** (build 261) и JVM 21. Расширение
`.volan` регистрируется автоматически. Код генерации и подключение к БД
для работы редактора не требуются. Объявления разрешаются внутри одного
файла, как в языке самой ORM.

## Установка

Скачайте подписанный ZIP из [GitHub Releases](https://github.com/thirtyeighttwentysix/volan-idea/releases).
Локальная сборка: `build/distributions/volan-idea-0.2.0.zip`.

В IDEA: **Settings → Plugins → ⚙ → Install Plugin from Disk…**, выбрать
ZIP, применить изменения и перезапустить IDE, если она предложит это.
Открыть [examples/schema.volan](examples/schema.volan), чтобы проверить
дополнение, документацию и переходы.

## Сборка и тестирование

Требуется JDK 21; Gradle Wrapper включён. Если Gradle работает под другим
JDK, установленный JDK 21 должен быть доступен Gradle toolchain discovery.

```powershell
.\gradlew.bat test buildPlugin verifyPluginStructure
```

По умолчанию Gradle скачивает IntelliJ IDEA 2026.1.3. Можно использовать
установленную IDE, чтобы избежать скачивания дистрибутива:

```powershell
.\gradlew.bat test buildPlugin verifyPluginStructure "-PlocalIdePath=C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3"
.\gradlew.bat verifyPlugin "-PlocalIdePath=C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3"
.\gradlew.bat runIde "-PlocalIdePath=C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3"
```

`runIde` запускает отдельную тестовую IDEA с плагином. Основная
установка IDE при этом не изменяется. Для проверки API-совместимости
другой версии подключайте JetBrains Plugin Verifier перед расширением
`sinceBuild`/`untilBuild` в `build.gradle.kts`.

Тесты проверяют работу через IntelliJ Platform: регистрацию файлов,
реальное дополнение и вставку, диагностику, документацию, разрешение
ссылок, сворачивание, инвалидирование кеша и Reformat Code. Отдельно
проверяются контексты дополнения в незавершённых схемах и перезапуск лексера.

## Форматирование

Выделение фрагментов не поддерживается: форматируется весь файл без
выделения. При ошибках **синтаксиса** команда оставляет файл без изменений,
чтобы восстановленный AST не удалил непонятный текст. Семантические ошибки
(например, неизвестный тип) форматированию не мешают. Настройка отступов
следует каноническому формату Volan: два пробела. Автоматическое
форматирование при наборе не запускает форматтер всего документа.

## Обновление движка ORM

В `vendor/` сохранены исходные `volan-core`, `volan-schema`, `volan-ir`
из локального проекта; версия зафиксирована в `vendor/NOTICE.md`.
Это позволяет собрать ZIP без установленной или опубликованной ORM.

```powershell
.\scripts\sync-engine.ps1 -OrmPath D:\Projects\Volan
.\gradlew.bat test buildPlugin "-PlocalIdePath=C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3"
```

Скрипт требует PowerShell 7, обновляет три каталога исходников движка и NOTICE; перед
изменениями проверяет пути и все входные каталоги. При развитии языка
также актуализируйте `VolanCatalog` и контексты `VolanCompletion`.
Диагностика и форматтер следуют скопированному движку автоматически.

## CI, релизы и Marketplace

Готовые изображения для галереи плагина: [assets/marketplace](assets/marketplace/README.md).
Четыре PNG размером **1586 × 992** с логотипом, кодом, дополнением, диагностикой
и форматированием; порядок загрузки и промпты сохранены рядом с файлами.

GitHub Actions проверяет тесты на Windows/Linux и совместимость через Plugin Verifier.
Теги `v*` создают подписанный GitHub Release с ZIP и SHA-256. Workflow Marketplace
публикует проверенный архив релиза; его можно запустить вручную или автоматически
после релиза. Первая регистрация плагина выполняется в интерфейсе JetBrains.

Настройки токена, каналов, версий и полный порядок выпуска:
[docs/releasing.md](docs/releasing.md). Правила участия: [CONTRIBUTING.md](CONTRIBUTING.md).

Основа сборки: [IntelliJ Platform Gradle Plugin 2.x](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html).
Лицензия: Apache-2.0, см. [LICENSE](LICENSE).
