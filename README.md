# Almasix Idea (PyCharm / WebStorm)

Native JetBrains plugin for [Almasix](https://github.com/almasix-dev/almasix)
applications — Prism file type, deep completions, navigation, rename,
intentions, and Smith run configurations — driven by
`smith ide:index --json`.

| | |
| --- | --- |
| **Marketplace** | JetBrains Marketplace — search **Almasix** (plugin id `com.almasix.ide`) |
| **Releases** | [GitHub Releases](https://github.com/almasix-dev/almasix-idea/releases) (`.zip`) |
| **Framework docs** | [Editor setup](https://almasix-dev.github.io/almasix/editor-setup/) · [Prism](https://almasix-dev.github.io/almasix/prism/) · [Language server](https://almasix-dev.github.io/almasix/language-server/) |
| **Sister extension** | [almasix-vscode](https://github.com/almasix-dev/almasix-vscode) (VS Code / Cursor / VSCodium) |

> Split from the former monorepo [`almasix-dev/ide-support`](https://github.com/almasix-dev/ide-support)
> (redirect only). VS Code lives in [`almasix-vscode`](https://github.com/almasix-dev/almasix-vscode).

**Supported products:** PyCharm Professional, PyCharm Community, and WebStorm.
Installing, updating, or removing the plugin requires an IDE restart.

---

## How it works

```text
Almasix app (bootstrap/app.py)
        │
        ▼
  Project interpreter (.venv / configured SDK)
        │
        ▼
  smith ide:index --json     ← or: python -m almasix.ide.index
        │
        ▼
  Plugin-owned index cache   ← Almasix → Rebuild Index; file watchers
        │
        ├── Completions + unknown-symbol annotators
        ├── Go to Declaration / Find Usages / Safe Rename
        ├── Hover / Quick Doc + Prism structure annotator
        ├── Intentions / code actions + Almasix Tool Window
        └── Smith run configuration type
```

Almasix Idea is **native-heavy**: Prism is a first-class file type with a
native highlighter (HTML spans + Prism overlays), not TextMate and **not**
LSP4IJ / `almasix-lsp`. That LSP path is for VS Code and other editors —
see [Language server](https://almasix-dev.github.io/almasix/language-server/).

Intelligence (routes, views, config, …) comes from the same JSON dump both
IDEs consume. Framework overview:
[Editor setup](https://almasix-dev.github.io/almasix/editor-setup/).
Feature matrix vs VS Code:
[almasix-vscode/PARITY.md](https://github.com/almasix-dev/almasix-vscode/blob/main/PARITY.md).

---

## Requirements

- **PyCharm** or **WebStorm** (2024.2+ recommended; build against current
  `platformVersion` in `gradle.properties`)
- An Almasix app with `bootstrap/app.py`
- **Almasix** on the **project interpreter** (prefer **0.9.1+**; current line
  **0.9.3+**)

```bash
# from the application root, with the project venv active
pip install -U almasix
smith ide:index --json | head
```

`smith ide:install` also writes `.idea/almasix-editor.md` with install notes.

---

## Install

1. Install **Almasix** from the JetBrains Marketplace (search **Almasix**,
   plugin id `com.almasix.ide`), **or**
   **Settings → Plugins → ⚙ → Install Plugin from Disk…** with a zip from
   [GitHub Releases](https://github.com/almasix-dev/almasix-idea/releases)
   (**0.3.3+**).
2. Restart when prompted.
3. Open the Almasix app and set the project interpreter to the env that has
   Almasix (or a `.venv` next to `bootstrap/app.py`).
4. Confirm Prism files open as language **Prism** (not HTML).
5. Force refresh when needed: **Almasix → Rebuild Index** (also under
   **Tools → Almasix**).

Optional from the app root:

```bash
smith ide:install
smith ide:stubs      # .pyi for models + route name Literal
```

---

## Features

### Prism (`.prism.html`)

- Native file type with HTML highlighting layered under Prism overlays
  (`@directives`, `{{ }}`, `{{-- --}}`)
- Second HTML PSI root so HTML completion / inspections keep working
- Typing `{{` closes as `{{  }}` with the caret in the middle
- Structure diagnostics for unmatched `@if` / `@endif` (and related pairs);
  inline `@section('name', 'value')` is self-closing; `@show` closes `@section`
- Prism file icon on the file type

See [Prism language support](https://almasix-dev.github.io/almasix/prism-language/)
and the [Prism guide](https://almasix-dev.github.io/almasix/prism/).

### Project intelligence (from `ide:index`)

Completions and unknown-string annotations cover:

- Routes, views, config keys, translations, middleware, env keys
- Tables / columns, Articulate relations and casts, gates, validation rules
- Disks / queues / caches, Prism components and directives
- Vite entries, Inertia pages, Smith command names

Navigation and refactor:

| Action | Coverage |
| --- | --- |
| **Ctrl-click / Go to Declaration** | Routes, views, config keys, env, components, tables/columns, relations, Prism `{{ vars }}` |
| **Ctrl-hover underline** | Navigable Almasix symbols |
| **Hover / Quick Doc** | Indexed symbols |
| **Find Usages** | Indexed call sites |
| **Safe Rename** | Incl. config leaf rewrite and view move where supported |

Soft-known-only surfaces (e.g. some `GATE` / `MIDDLEWARE` / `VALIDATION` tokens)
get completions without go-to-declaration — same shared limitation as VS Code
([PARITY.md](https://github.com/almasix-dev/almasix-vscode/blob/main/PARITY.md)).

### Articulate / ORM helpers

Column completion for patterns such as `User.where("…")`, `fillable` /
`guarded` / `casts`, and `auth().user().…` / `request.user().…`.

### Env

Two-way env completion: keys from config in `.env`, and driver / bulk options
(e.g. `MAIL_*` insert).

### UI and generators

| Surface | What it does |
| --- | --- |
| **Almasix → Rebuild Index** | Re-run `smith ide:index --json` |
| **Almasix → New…** (+ IDE **New** menu) | Full `smith make:*` generator menu |
| **Almasix Tool Window** | Index health + searchable symbol browser |
| **Smith run configurations** | Serve / migrate / test / queue workers, etc. |

### Intentions / refactor polish

- Create missing view or component
- Alt-Enter `smith make:*` shortcuts
- Extract Prism partial; `@include` → `<x-… />`; relation method stub

---

## Troubleshooting

**No completions / empty tool window**

- Confirm the **project interpreter** can run `smith ide:index --json`.
- Prefer a project-local `.venv` with `pip install almasix`.
- **Almasix → Rebuild Index** after changing the SDK.

**`.prism.html` opens as HTML**

- File type must be **Prism**. Re-open the file or check
  **Settings → Editor → File Types**.
- `smith ide:install` documents the expected associations in
  `.idea/almasix-editor.md`.

**Plugin not offered on IntelliJ IDEA / other IDEs**

- Only PyCharm and WebStorm are supported (see `plugin.xml`
  `incompatible-with` list).

**Stale symbols after large changes**

- **Almasix → Rebuild Index**.

More context: [Editor setup](https://almasix-dev.github.io/almasix/editor-setup/).

---

## Develop

```bash
./gradlew test buildPlugin
# → build/distributions/almasix-*.zip
```

Requires **JDK 17+**. Version is `pluginVersion` in `gradle.properties`.
Line coverage gate (≥ 98%): [COVERAGE.md](COVERAGE.md).

---

## Release

Create a GitHub Release on a `vX.Y.Z` tag. The
[publish workflow](.github/workflows/publish.yml) builds the zip, attaches it
to the Release, and publishes to the JetBrains Marketplace.

Secret: `JETBRAINS_PUBLISH_TOKEN`.

---

## Documentation map

| Topic | Where |
| --- | --- |
| Install both IDEs + `ide:install` / `ide:index` / stubs | [Editor setup](https://almasix-dev.github.io/almasix/editor-setup/) |
| Prism templates (directives, layouts, components) | [Prism](https://almasix-dev.github.io/almasix/prism/) |
| Grammars, formatter, snippets | [Prism language support](https://almasix-dev.github.io/almasix/prism-language/) |
| Optional `almasix-lsp` (other editors; not this plugin) | [Language server](https://almasix-dev.github.io/almasix/language-server/) |
| Smith CLI | [Console](https://almasix-dev.github.io/almasix/console/) |
| Framework install | [Installation](https://almasix-dev.github.io/almasix/installation/) |
| VS Code ↔ JetBrains feature matrix | [PARITY.md](https://github.com/almasix-dev/almasix-vscode/blob/main/PARITY.md) |
| VS Code / Cursor extension | [almasix-vscode](https://github.com/almasix-dev/almasix-vscode) |
| Framework source | [almasix-dev/almasix](https://github.com/almasix-dev/almasix) |

License: [MIT](LICENSE). Coverage notes: [COVERAGE.md](COVERAGE.md). Changelog:
[CHANGELOG.md](CHANGELOG.md).
