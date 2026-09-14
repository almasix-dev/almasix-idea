# Coverage

Almasix Idea enforces **≥ 98% line coverage** on product logic via
[Kover](https://github.com/Kotlin/kotlinx-kover):

```bash
cd jetbrains
./gradlew test koverHtmlReport koverVerify
# HTML: build/reports/kover/html/index.html
```

`./gradlew check` runs `koverVerify` and fails the build under 98%.

## Policy

- New intelligence (call-site detection, rename plans, code-action planners,
  index parsing, generators) lives in **unit-testable** objects.
- Exclusions in `build.gradle.kts` are limited to thin IntelliJ Platform shells
  (listeners, run-config UI, highlighter providers, PSI contributor wiring).
- Do **not** park feature logic in excluded classes to dodge the gate.
