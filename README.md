# Almasix for JetBrains (PyCharm / IntelliJ)

**Almasix Idea** — native JetBrains plugin (Laravel Idea analogue):

- Prism file type with **native** HTML + Prism highlighting
- Deep completions + unknown-symbol annotations driven by
  `smith ide:index --json`
- Smith run configurations
- **Almasix** menu (main menu bar + Tools) → **Rebuild Index**

Does **not** use LSP4IJ / `almasix-lsp` (that path is for VS Code).

## Install

1. Install **Almasix** from the JetBrains Marketplace (id `com.almasix.ide`),
   **or** **Settings → Plugins → ⚙ → Install Plugin from Disk…** with a zip from
   [GitHub Releases](https://github.com/almasix-dev/ide-support/releases)
   (**0.2.1+** recommended; **0.2.0+** for native Almasix Idea).
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

Marketplace publish uses `JETBRAINS_PUBLISH_TOKEN` via the root repo
[publish workflow](../.github/workflows/publish.yml).

## Docs

Framework docs: [Editor setup](https://almasix-dev.github.io/almasix/editor-setup/).
