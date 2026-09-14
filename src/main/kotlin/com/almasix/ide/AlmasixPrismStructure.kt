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

    private val OPENERS = PAIRS.keys
    private val CLOSERS = PAIRS.values.toSet()
    private val DIRECTIVE = Regex("""@([A-Za-z_][\w]*)""")

    fun analyze(text: String): List<Issue> {
        data class Frame(val name: String, val start: Int, val end: Int)
        val stack = ArrayDeque<Frame>()
        val issues = mutableListOf<Issue>()
        for (m in DIRECTIVE.findAll(text)) {
            val name = m.groupValues[1]
            val start = m.range.first
            val end = m.range.last + 1
            when {
                name in OPENERS -> stack.addLast(Frame(name, start, end))
                name in CLOSERS -> {
                    if (stack.isEmpty()) {
                        issues.add(Issue(start, end, "Unexpected @$name (no matching open)"))
                        continue
                    }
                    val top = stack.removeLast()
                    val expected = PAIRS[top.name]
                    if (expected != name) {
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
                // elseif/else/show are mid-block — ignore for stack balance
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
