# Contributing

Use JDK 21 and the committed Gradle Wrapper. IntelliJ IDEA is downloaded automatically.

```shell
./gradlew test buildPlugin verifyPluginStructure
./gradlew verifyPlugin
./gradlew runIde
```

On Windows use `gradlew.bat`. A local IDE can be selected with
`-PlocalIdePath="C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3"`.

Add focused tests for changes to completion, diagnostics, references and formatting.
Tests under `src/test/` include real IntelliJ Platform fixtures. Use
`examples/schema.volan` for manual editor checks. Keep UI code in Kotlin and avoid
database connections in editor features.

The files under `vendor/` are an attributed source snapshot. Update them using
`scripts/sync-engine.ps1`, and update the source revision in `vendor/NOTICE.md`.
Edit language behavior upstream in [Volan](https://github.com/thirtyeighttwentysix/volan).
Run tests after a snapshot update.

Open a pull request against `main`. Include the user-visible behavior and relevant
validation. Add a changelog entry for user-visible changes. IntelliJ compatibility
must be verified before widening `sinceBuild` / `untilBuild`.

Release instructions are in [docs/releasing.md](docs/releasing.md).
