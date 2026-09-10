# Almasix for JetBrains (PyCharm / IntelliJ)

LSP-first plugin shell: Prism file type with **native** syntax highlighting,
Smith run configurations, and **almasix-lsp** via
[LSP4IJ](https://github.com/redhat-developer/lsp4ij).

## Install

1. Install **Almasix** from the JetBrains Marketplace (id `com.almasix.ide`),
   **or** **Settings → Plugins → ⚙ → Install Plugin from Disk…** with a zip from
   [GitHub Releases](https://github.com/almasix-dev/editors/releases).
2. Restart when prompted.
3. Open an Almasix app (folder with `bootstrap/app.py`). Ensure the project venv
   has the language server:

   ```bash
   pip install 'almasix[lsp]'
   ```

4. LSP4IJ starts `almasix-lsp` from `.venv/bin/almasix-lsp` (or
   `python -m almasix.lsp`). Prism (`.prism.html`) and Python files are mapped.

## Develop

```bash
cd jetbrains
./gradlew test buildPlugin
# → build/distributions/almasix-jetbrains-*.zip
```

Requires JDK 17+. Version is `pluginVersion` in `gradle.properties`.

Marketplace publish uses `JETBRAINS_PUBLISH_TOKEN` via the root repo
[publish workflow](../.github/workflows/publish.yml).

## Docs

Framework docs: [Editor setup](https://almasix-dev.github.io/almasix/editor-setup/).
