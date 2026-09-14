# Almasix for JetBrains (PyCharm / IntelliJ)

**Almasix Idea** — native JetBrains plugin (Laravel Idea analogue):

- Prism file type with **native** HTML + Prism highlighting
- Deep completions + unknown-symbol annotations driven by
  `smith ide:index --json`
- **Ctrl-click / Go to Declaration** for routes, views, config **keys** (not just the
  file), env, components, tables/columns, relations, and Prism `{{ vars }}`
- Ctrl-hover **underline** on navigable Almasix symbols
- **Hover / Quick Doc** (Ctrl-Q) for routes, views, config, env, components, …
- **Find Usages** (Alt-F7) for routes, views, config keys, components, and env keys
- **Safe Rename** (Shift-F6) for the same call-site symbols
- **Code actions** — create missing view/component; Alt-Enter `smith make:*` suggestions
- **Refactor polish** — extract Prism partial; `@include` → `<x-… />`; relation method stub
- **Almasix → New…** (+ IDE **New** menu) — full `smith make:*` generator menu
- **Almasix Tool Window** — index health + searchable symbol browser
- **Prism structure** diagnostics for unmatched `@if` / `@endif` (and friends)
- **Articulate / ORM column completion** from migrations — `User.where("…")`,
  `fillable` / `guarded` / `casts` keys, and **`auth().user().…`** / `request.user().…`
- **Hover / Quick Doc** (Ctrl-Q) for routes, views, config, env, components, …
- **Two-way env completion** — keys from config in `.env`, and driver/store options
  (e.g. `QUEUE_CONNECTION` → `sync` / `redis`)
- Smith run configurations
- **Almasix** menu (main menu bar + Tools) → **Rebuild Index** / **New…**
- **≥ 98% line coverage** gate on product logic ([COVERAGE.md](COVERAGE.md); aim ~100%)

Does **not** use LSP4IJ / `almasix-lsp` (that path is for VS Code).

## Install

1. Install **Almasix** from the JetBrains Marketplace (id `com.almasix.ide`),
   **or** **Settings → Plugins → ⚙ → Install Plugin from Disk…** with a zip from
   [GitHub Releases](https://github.com/almasix-dev/ide-support/releases)
   (**0.3.0** exhaust WIP — Find Usages + Rename + …; **0.2.3+** for key-level
   config nav / env value completion). Prefer Almasix **0.9.1+** on the project
   interpreter.
2. Restart when prompted.
3. Open an Almasix app (folder with `bootstrap/app.py`). Ensure the **project
   interpreter** (or a `.venv` beside that app) has Almasix installed:

   ```bash
   pip install almasix
   # confirm:
   smith ide:index --json | head
   ```

4. The plugin rebuilds the index on project open and when routes/config/views/
   models change. Force a refresh from the menu bar:
   **Almasix → Rebuild Index** (also under **Tools → Almasix**).
   Or **Find Action** (`Ctrl+Shift+A` / `⌘⇧A`) → `Rebuild Index`.

## Develop

```bash
cd jetbrains
./gradlew test buildPlugin
# → build/distributions/almasix-*.zip
```

Requires JDK 17+. Version is `pluginVersion` in `gradle.properties`.
Coverage gate (≥ 98% line): see [COVERAGE.md](COVERAGE.md).

Marketplace publish uses `JETBRAINS_PUBLISH_TOKEN` via the root repo
[publish workflow](../.github/workflows/publish.yml).

## Docs

Framework docs: [Editor setup](https://almasix-dev.github.io/almasix/editor-setup/).
