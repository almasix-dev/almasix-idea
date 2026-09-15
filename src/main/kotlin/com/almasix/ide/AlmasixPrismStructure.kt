package com.almasix.ide

/**
 * Prism block-structure checks: unmatched open/close directives.
 */
object AlmasixPrismStructure {
    data class Issue(
        val startOffset: Int,
        val endOffset: Int,
        val message: String,
    )

    /** Open directive → expected closer. */
    val PAIRS: Map<String, String> = mapOf(
        "if" to "endif",
        "unless" to "endunless",
        "isset" to "endisset",
        "empty" to "endempty",
        "for" to "endfor",
        "foreach" to "endforeach",
        "forelse" to "endforelse",
        "while" to "endwhile",
        "section" to "endsection",
        "component" to "endcomponent",
        "slot" to "endslot",
        "push" to "endpush",
        "prepend" to "endprepend",
        "once" to "endonce",
        "python" to "endpython",
        "error" to "enderror",
        "auth" to "endauth",
        "guest" to "endguest",
        "can" to "endcan",
        "cannot" to "endcannot",
        "canany" to "endcanany",
        "cannotany" to "endcannotany",
        "cache" to "endcache",
    )

    /** Closers that alias another PAIRS value (compiler: `@show` ends `@section`). */
    private val CLOSER_ALIASES: Map<String, String> = mapOf(
        "show" to "endsection",
    )

    /** Openers that may be one-line / self-closing when args include a value. */
    private val INLINEABLE: Set<String> = setOf("section")

    private val OPENERS = PAIRS.keys
    private val CLOSERS = PAIRS.values.toSet() + CLOSER_ALIASES.keys
    private val DIRECTIVE = Regex("""@([A-Za-z_][\w]*)""")

    /**
     * True when `@section('name', 'value')` (compiler `section_inline`):
     * a top-level comma inside the directive's parentheses.
     */
    fun isInlineDirective(text: String, atNameEnd: Int): Boolean {
        var i = atNameEnd
        while (i < text.length && text[i].isWhitespace()) i++
        if (i >= text.length || text[i] != '(') return false
        i++ // past '('
        var depth = 1
        var quote: Char? = null
        var sawTopLevelComma = false
        while (i < text.length && depth > 0) {
            val ch = text[i]
            when {
                quote != null -> {
                    if (ch == '\\' && i + 1 < text.length) {
                        i += 2
                        continue
                    }
                    if (ch == quote) quote = null
                    i++
                }
                ch == '\'' || ch == '"' -> {
                    quote = ch
                    i++
                }
                ch == '(' -> {
                    depth++
                    i++
                }
                ch == ')' -> {
                    depth--
                    i++
                }
                else -> {
                    if (ch == ',' && depth == 1) sawTopLevelComma = true
                    i++
                }
            }
        }
        return sawTopLevelComma && depth == 0
    }

    fun analyze(text: String): List<Issue> {
        data class Frame(val name: String, val start: Int, val end: Int)
        val stack = ArrayDeque<Frame>()
        val issues = mutableListOf<Issue>()
        for (m in DIRECTIVE.findAll(text)) {
            val name = m.groupValues[1]
            val start = m.range.first
            val end = m.range.last + 1
            when {
                name in OPENERS -> {
                    if (name in INLINEABLE && isInlineDirective(text, end)) {
                        continue
                    }
                    stack.addLast(Frame(name, start, end))
                }
                name in CLOSERS -> {
                    val closer = CLOSER_ALIASES[name] ?: name
                    if (stack.isEmpty()) {
                        issues.add(Issue(start, end, "Unexpected @$name (no matching open)"))
                        continue
                    }
                    val top = stack.removeLast()
                    val expected = PAIRS[top.name]
                    if (expected != closer) {
                        issues.add(
                            Issue(
                                start,
                                end,
                                "Expected @$expected to close @${top.name}, found @$name",
                            ),
                        )
                        // Put it back so further closes can still match.
                        stack.addLast(top)
                    }
                }
                // elseif/else are mid-block — ignore for stack balance
            }
        }
        for (frame in stack) {
            issues.add(
                Issue(
                    frame.start,
                    frame.end,
                    "Unclosed @${frame.name} (expected @${PAIRS[frame.name]})",
                ),
            )
        }
        return issues
    }
}
