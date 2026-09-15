# Almasix for JetBrains (PyCharm / WebStorm)

**Almasix Idea** — native JetBrains plugin (Laravel Idea analogue) for
**PyCharm Professional**, **PyCharm Community**, and **WebStorm**.

> Split from the former monorepo [`almasix-dev/ide-support`](https://github.com/almasix-dev/ide-support).
> VS Code lives in [`almasix-dev/almasix-vscode`](https://github.com/almasix-dev/almasix-vscode).

- Prism file type with **native** HTML + Prism highlighting
- Deep completions + unknown-symbol annotations driven by
  `smith ide:index --json`
- **Ctrl-click / Go to Declaration** for routes, views, config **keys**, env,
  components, tables/columns, relations, and Prism `{{ vars }}`
- Ctrl-hover **underline** on navigable Almasix symbols
- **Hover / Quick Doc**, **Find Usages**, **Safe Rename**
- **Code actions** — create missing view/component; Alt-Enter `smith make:*`
- **Refactor polish** — extract Prism partial; `@include` → `<x-… />`; relation stub
- **Almasix → New…** (+ IDE **New** menu) — full `smith make:*` generator menu
- **Almasix Tool Window** — index health + searchable symbol browser
- **Prism structure** diagnostics for unmatched `@if` / `@endif`
- **Articulate / ORM column completion** — `User.where("…")`, `fillable` /
  `guarded` / `casts`, `auth().user().…` / `request.user().…`
- **Two-way env completion** — keys from config in `.env`, and driver options
- Smith run configurations; **≥ 98% line coverage** gate ([COVERAGE.md](COVERAGE.md))

Does **not** use LSP4IJ / `almasix-lsp` (that path is for VS Code).

## Install

1. Install **Almasix** from the JetBrains Marketplace (id `com.almasix.ide`),
   **or** **Settings → Plugins → ⚙ → Install Plugin from Disk…** with a zip from
   [GitHub Releases](https://github.com/almasix-dev/almasix-idea/releases)
   (**0.3.3+**; PyCharm / WebStorm only). Prefer Almasix **0.9.1+** on the project
   interpreter.
2. Restart when prompted.
3. Open an Almasix app (`bootstrap/app.py`). Ensure the **project interpreter**
   (or `.venv`) has Almasix:

   ```bash
   pip install almasix
   smith ide:index --json | head
   ```

4. Force refresh: **Almasix → Rebuild Index** (also **Tools → Almasix**).

## Develop

```bash
./gradlew test buildPlugin
# → build/distributions/almasix-*.zip
```

Requires JDK 17+. Version is `pluginVersion` in `gradle.properties`.
Coverage gate (≥ 98% line): see [COVERAGE.md](COVERAGE.md).

## Release

Create a GitHub Release on a `vX.Y.Z` tag. The [publish workflow](.github/workflows/publish.yml)
builds the zip, attaches it to the Release, and publishes to the JetBrains Marketplace.

Secret: `JETBRAINS_PUBLISH_TOKEN`.

## Docs

Framework docs: [Editor setup](https://almasix-dev.github.io/almasix/editor-setup/).
