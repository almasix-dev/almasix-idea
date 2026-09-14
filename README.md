# Almasix for JetBrains (PyCharm / IntelliJ)

LSP-first plugin shell: Prism file type with **native** syntax highlighting,
Smith run configurations, and **almasix-lsp** via
[LSP4IJ](https://github.com/redhat-developer/lsp4ij).

## Install

1. Install **Almasix** from the JetBrains Marketplace (id `com.almasix.ide`),
   **or** **Settings → Plugins → ⚙ → Install Plugin from Disk…** with a zip from
   [GitHub Releases](https://github.com/almasix-dev/ide-support/releases).
2. Restart when prompted.
3. Open an Almasix app (folder with `bootstrap/app.py`). Ensure the **project
   interpreter** (or a `.venv` beside that app) has the language server:

   ```bash
   pip install 'almasix[lsp]'
   # confirm:
   python -m almasix.lsp --help   # or: which almasix-lsp
   ```

4. LSP4IJ starts `almasix-lsp` by resolving, in order:
   - the IDE project Python interpreter → `python -m almasix.lsp`
   - `.venv` / `venv` next to `bootstrap/app.py`
   - `.venv` / `venv` at the project root
   - `almasix-lsp` on `PATH`

   Prism (`.prism.html`) and Python files are mapped.

### Troubleshooting ``Cannot start server … almasixLsp (pid=null)``

That message means the IDE never spawned a process. Usual causes:

1. **Wrong folder open** — open the app root (has `bootstrap/app.py`), not a
   parent monorepo folder without a usable venv.
2. **Missing extra** — `pip install 'almasix[lsp]'` into the interpreter
   PyCharm shows under **Settings → Project → Python Interpreter**.
3. **LSP4IJ missing** — the Almasix plugin depends on
   [LSP4IJ](https://plugins.jetbrains.com/plugin/23257-lsp4ij); install it and
   restart.
4. Check **Language Servers** tool window → Almasix → Log for the resolved
   command after 0.1.12+.

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
