# Changelog

## 0.3.5

- Prism structure: treat inline `@section('name', 'value')` as self-closing;
  accept `@show` as a `@section` closer (matches the Prism compiler).

## 0.3.4

- `pluginIcon.svg` synced to the same official `almasix.svg` mark used by the
  VS Code extension (in-IDE `icons/almasix.svg` already matched).

## 0.3.3

First release from [`almasix-dev/almasix-idea`](https://github.com/almasix-dev/almasix-idea)
(split from the former `ide-support` monorepo). Same Almasix Idea surface as
**0.3.2** (PyCharm Professional / Community + WebStorm only); packaging and
Marketplace publish now run from this repository.

## 0.3.2

Limit supported IDEs to PyCharm and WebStorm.

## 0.3.1

Hyperlinks, Blueprint quiet, scaffolder parity, interactive `make:model`, env bulk.

## 0.3.0

Find usages, rename, and ORM depth.
