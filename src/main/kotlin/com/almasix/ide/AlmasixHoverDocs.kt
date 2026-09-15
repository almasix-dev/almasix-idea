package com.almasix.ide

/**
 * Hover / quick-doc text for Almasix symbols and Prism directives.
 * Kept in sync with `almasix.lsp.features._hover_for_call` + `directives.PRISM_DIRECTIVES`.
 */
object AlmasixHoverDocs {
    /** Baseline Prism directive docs (without leading `@`). */
    val DIRECTIVES: Map<String, String> = mapOf(
        "if" to "`@if(condition)` — conditional block; close with `@endif`.",
        "elseif" to "`@elseif(condition)` — else-if branch inside `@if`.",
        "else" to "`@else` — final branch inside `@if` / `@unless`.",
        "endif" to "`@endif` — closes an `@if` block.",
        "unless" to "`@unless(condition)` — inverted conditional; close with `@endunless`.",
        "endunless" to "`@endunless` — closes an `@unless` block.",
        "foreach" to "`@foreach(items as item)` — iterate a collection; `@endforeach`.",
        "endforeach" to "`@endforeach` — closes a `@foreach` block.",
        "forelse" to "`@forelse(items as item)` — foreach with `@empty` / `@endforelse`.",
        "endforelse" to "`@endforelse` — closes a `@forelse` block.",
        "for" to "`@for(...)` — loop; `@endfor`.",
        "endfor" to "`@endfor` — closes an `@for` block.",
        "while" to "`@while(condition)` — while loop; `@endwhile`.",
        "endwhile" to "`@endwhile` — closes a `@while` block.",
        "extends" to "`@extends('layout')` — inherit a layout view.",
        "include" to "`@include('partial')` — render another view inline.",
        "component" to "`@component('name')` — component; `@endcomponent`.",
        "endcomponent" to "`@endcomponent` — closes a `@component` block.",
        "section" to
            "`@section('name')` … `@endsection` / `@show`, or inline `@section('name', 'value')`.",
        "endsection" to "`@endsection` — closes a `@section` block.",
        "yield" to "`@yield('name')` — render a section from a child view.",
        "csrf" to "`@csrf` — hidden CSRF token field.",
        "vite" to "`@vite(['app.js'])` — Vite entry tags.",
        "route" to "`@route('name')` — named route URL.",
        "auth" to "`@auth` — render when authenticated; `@endauth`.",
        "guest" to "`@guest` — render for guests; `@endguest`.",
        "can" to "`@can('ability')` — authorization gate; `@endcan`.",
        "lang" to "`@lang('key')` — translate a key.",
        "props" to "`@props({…})` — declare component props with defaults.",
        "slot" to "`@slot('name')` — named slot body; `@endslot`.",
        "push" to "`@push('stack')` — append to a stack; `@endpush`.",
        "stack" to "`@stack('name')` — render a named stack.",
        "python" to "`@python` — embedded Python block; `@endpython`.",
    )

    fun directive(name: String): String? = DIRECTIVES[name]

    fun forSymbol(index: AlmasixIndex, kind: SymbolKind, name: String, receiver: String? = null): String? {
        if (name.isBlank()) return null
        return when (kind) {
            SymbolKind.ROUTE -> {
                val route = index.routes[name]
                if (route == null) "**route** `$name`\n\n_Unknown named route._"
                else {
                    val methods = route.methods.joinToString(" | ")
                    val loc = route.path?.let { "\n\n`$it:${route.line + 1}`" }.orEmpty()
                    "**route** `$name`\n\n`$methods` `${route.uri}`$loc"
                }
            }
            SymbolKind.VIEW -> {
                val path = index.views[name]
                if (path == null) "**view** `$name`\n\n_Not found in resources/views._"
                else "**view** `$name`\n\n`$path`"
            }
            SymbolKind.CONFIG -> {
                val known = index.configKeys.contains(name)
                val status = if (known) "indexed" else "not in config/*.py"
                val loc = index.configLocations[name]?.path?.let { "\n\n`$it`" }.orEmpty()
                "**config** `$name`\n\n$status$loc"
            }
            SymbolKind.ENV -> {
                val entry = index.envKeys[name]
                if (entry == null) "**env** `$name`\n\n_Unknown key._"
                else {
                    val detail = entry.detail.ifBlank { entry.kind.ifBlank { "env" } }
                    val loc = entry.path?.let { "\n\n`$it:${entry.line + 1}`" }.orEmpty()
                    "**env** `$name`\n\n$detail$loc"
                }
            }
            SymbolKind.COMPONENT -> {
                val path = index.components[name] ?: index.views["components.$name"]
                if (path == null) "**component** `$name`\n\n_Not found._"
                else "**component** `$name`\n\n`$path`"
            }
            SymbolKind.RELATION -> {
                val rels = AlmasixCompletionCatalog.relationsFor(index, receiver)
                if (name in rels) "**relation** `$name`" + (receiver?.let { " on `$it`" }.orEmpty())
                else "**relation** `$name`\n\n_Unknown relation._"
            }
            SymbolKind.COLUMN, SymbolKind.ATTR, SymbolKind.MODEL_ATTR -> {
                val cols = AlmasixModelResolver.columnsFor(index, receiver)
                if (name in cols) "**column** `$name`" + (receiver?.let { " ($it)" }.orEmpty())
                else "**column** `$name`\n\n_Unknown column._"
            }
            SymbolKind.TEMPLATE_VAR -> {
                if (index.templateVarNames().contains(name.substringBefore('.'))) {
                    "**template var** `$name`"
                } else {
                    "**template var** `$name`\n\n_Unknown in view data / shared / helpers._"
                }
            }
            SymbolKind.DIRECTIVE -> directive(name)
            else -> {
                if (index.known(kind, name)) "**${kind.name.lowercase()}** `$name`"
                else null
            }
        }
    }
}
