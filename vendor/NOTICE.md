# Volan language engine snapshot

The `core`, `schema` and `ir` directories are unmodified Kotlin sources from
https://github.com/thirtyeighttwentysix/volan, commit
`1a35a55a1f95a7bec9cfe455ee5f18420e40e1ce`, copied on 2026-10-01.
They are licensed under Apache-2.0 (see ../LICENSE).

The snapshot makes this plugin independently buildable and keeps syntax diagnostics,
semantic validation and formatting identical to the ORM at this revision. The IDE
never runs code generation, connects to a database or reads environment variables.
To update, use ../scripts/sync-engine.ps1 and run the tests afterwards.
