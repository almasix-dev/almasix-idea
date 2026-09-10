package com.almasix.ide

/**
 * Directive names the lexer will color.
 *
 * Matching a known name — rather than "`@` followed by a word" — is what keeps
 * `hi@example.com` out of the highlighter. Kept in sync with
 * `src/almasix/lsp/directives.py` by `tests/smoke/test_m47_smoke.py`.
 */
object PrismDirectives {
    val NAMES: Set<String> = setOf(
        "asset", "auth", "aware", "cache", "can", "canany", "cannot", "cannotany", "choice",
        "component", "csrf", "dd", "dump", "each", "else", "elseif", "empty", "endauth",
        "endcache", "endcan", "endcanany", "endcannot", "endcannotany", "endcomponent", "endempty",
        "enderror", "endfor", "endforeach", "endforelse", "endguest", "endif", "endisset",
        "endonce", "endprepend", "endpush", "endpython", "endsection", "endslot", "endunless",
        "endwhile", "error", "extends", "for", "foreach", "forelse", "guest", "if", "include",
        "includeIf", "includeUnless", "includeWhen", "isset", "lang", "once", "parent", "prepend",
        "props", "push", "python", "route", "section", "show", "signedRoute", "slot", "stack",
        "unless", "vite", "viteReactRefresh", "while", "yield",
    )
}
