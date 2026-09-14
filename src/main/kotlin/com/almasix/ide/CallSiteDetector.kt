package com.almasix.ide

/**
 * Detect which Almasix string / attribute surface the caret is inside.
 */
object CallSiteDetector {
    data class Site(
        val kind: SymbolKind,
        val prefix: String,
        /** Model / table hint for columns / relations; env key for ENV_VALUE. */
        val receiver: String? = null,
        /** True when completing a pipe-segment of a validation rule string. */
        val validationSegment: Boolean = false,
    )

    private val COLUMN_FNS =
        "where_not_between|where_between|where_json_contains|where_json_length|" +
            "where_not_null|where_not_in|where_null|where_date|where_time|where_day|" +
            "where_month|where_year|where_not|where_in|or_where|where|" +
            "order_by_desc|order_by|group_by|having_between|having|" +
            "add_select|select|pluck|value|increment|decrement|" +
            "sum|avg|max|min|latest|oldest|only|except|update|create|" +
            "first_or_create|update_or_create|first_or_new|find_or_new|" +
            "fill|force_fill"

    private val RELATION_FNS = "with_|load|load_missing|has|where_has|or_where_has|doesnt_have"

    private val METHOD_CALL = Regex(
        """(?<recv>\b[A-Za-z_][\w]*(?:\.[A-Za-z_][\w]*)*)\.(?<fn>route|route_is|view|config|__|trans|env|can|authorize|middleware|disk|render|$RELATION_FNS|$COLUMN_FNS|table|vite|asset|url)\s*\(\s*(?<q>['"])(?<pre>[^'"]*)\z""",
        RegexOption.IGNORE_CASE,
    )

    private val GLOBAL_CALL = Regex(
        """(?<![.\w])(?<fn>route|route_is|view|config|__|trans|env|can|authorize|middleware|disk|render|vite|asset|url)\s*\(\s*(?<q>['"])(?<pre>[^'"]*)\z""",
        RegexOption.IGNORE_CASE,
    )

    /** ``redirect().route("…`` — call after ``).``. */
    private val CHAINED_CALL = Regex(
        """\)\.(?<fn>route|route_is|view|config|__|trans|env|can|authorize|middleware|disk|render|$RELATION_FNS|$COLUMN_FNS|table|vite|asset|url)\s*\(\s*(?<q>['"])(?<pre>[^'"]*)\z""",
        RegexOption.IGNORE_CASE,
    )

    private val ENV_DEFAULT = Regex(
        """\benv\s*\(\s*(['"])(?<key>[A-Za-z_][\w]*)\1\s*,\s*(?:(['"])(?<pre>[^'"]*)|(?<bare>[A-Za-z_][\w.]*)?)?\z""",
    )

    private val DIRECTIVE_VIEW = Regex(
        """@(?:extends|include|includeIf|includeWhen|includeUnless|each|component|lang|choice|can|cannot|canany|cannotany|route|signedRoute|asset|vite)\s*\(\s*(?<q>['"])(?<pre>[^'"]*)\z""",
    )

    private val AT_DIRECTIVE = Regex("""@(?<pre>[A-Za-z_][\w]*)?\z""")

    private val COMPONENT_TAG = Regex("""<x-(?<pre>[\w./-]*)\z""")

    private val DOTENV_INTERPOLATION = Regex("""\$\{(?<pre>[A-Za-z_][\w]*)?\z""")

    private val DOTENV_VALUE = Regex(
        """^\s*(?:export\s+)?(?<key>[A-Za-z_][\w]*)\s*=\s*(?<pre>[^#]*)\z""",
    )

    private val DOTENV_KEY = Regex(
        """^\s*(?:export\s+)?(?<pre>[A-Za-z_][\w]*)?\z""",
    )

    private val VALIDATION = Regex(
        """(?<fn>validate|rules)\s*\([^)]*?(?:['"])(?<pre>[^'"]*)\z""",
        RegexOption.IGNORE_CASE,
    )

    /** ``casts = { "email": "dat…`` — cast *type* value. */
    private val CASTS_VALUE = Regex(
        """casts\s*=\s*\{[^}]*['"][^'"]+['"]\s*:\s*['"](?<pre>[^'"]*)\z""",
    )

    /** ``casts = { "ema…`` — column *key*. */
    private val CASTS_KEY = Regex(
        """casts\s*=\s*\{(?:[^}'"]*(?:['"][^'"]*['"]\s*:\s*['"][^'"]*['"]\s*,\s*)*)\s*['"](?<pre>[^'"]*)\z""",
    )

    /** ``fillable = ["ema…`` / ``guarded = ("pass…``. */
    private val MODEL_LIST = Regex(
        """\b(?<attr>fillable|guarded|hidden|appends)\s*=\s*(?:\[|\()\s*(?:(?:['"][^'"]*['"])\s*,\s*)*['"](?<pre>[^'"]*)\z""",
    )

    /**
     * Instance attribute: ``user.ema``, ``auth().user().ema``, ``request.user().na``.
     */
    private val ATTR = Regex(
        """(?:auth\s*\(\s*\)(?:\s*\.\s*guard\s*\([^)]*\))?\s*\.\s*user\s*\(\s*\)|request\s*\.\s*user\s*\(\s*\)|(?<recv>[A-Za-z_][\w]*))\.(?<pre>[A-Za-z_][\w]*)?\z""",
    )

    private val CONTROLLER_ACTION = Regex(
        """\[\s*[A-Za-z_][\w.]*\s*,\s*(?<q>['"])(?<pre>[^'"]*)\z""",
    )

    private val SMITH = Regex(
        """(?:Artisan::call|Smith\.call|call)\s*\(\s*(?<q>['"])(?<pre>[^'"]*)\z""",
    )

    private val MUTATOR_FNS =
        "create|update|fill|force_fill|first_or_create|update_or_create|first_or_new|find_or_new"

    /** ``Author.create(email=`` / ``user.update(name=``. */
    private val KWARG_COLUMN = Regex(
        """(?<recv>\b[A-Za-z_][\w]*(?:\.[A-Za-z_][\w]*)*)\.(?<fn>$MUTATOR_FNS)\s*\(\s*(?:.*,\s*)?(?<pre>[A-Za-z_][\w]*)?\s*=?\s*\z""",
        RegexOption.IGNORE_CASE,
    )

    /** ``Author.create({"ema`` / ``update({"na``. */
    private val DICT_COLUMN = Regex(
        """(?<recv>\b[A-Za-z_][\w]*(?:\.[A-Za-z_][\w]*)*)\.(?<fn>$MUTATOR_FNS)\s*\(\s*\{(?:[^}'"]*(?:['"][^'"]*['"]\s*:\s*[^,}]+,?\s*)*)\s*['"](?<pre>[^'"]*)\z""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * ``Author.query().where("`` — chained after ``query()`` / ``factory()`` so the
     * model class is recovered when [CHAINED_CALL] alone has no receiver.
     */
    private val MODEL_QUERY_CHAIN = Regex(
        """(?<recv>\b[A-Z][A-Za-z0-9_]*)\.(?:query|new_query|factory)\s*\([^)]*\)(?:\s*\.\s*[A-Za-z_][\w]*\s*\([^)]*\))*\s*\.\s*(?<fn>$RELATION_FNS|$COLUMN_FNS)\s*\(\s*(?<q>['"])(?<pre>[^'"]*)\z""",
        RegexOption.IGNORE_CASE,
    )

    private val COLUMN_FN_SET = COLUMN_FNS.split('|').map { it.lowercase() }.toSet()
    private val RELATION_FN_SET = RELATION_FNS.split('|').map { it.lowercase() }.toSet()
    private val CALL_MATCHERS = listOf(METHOD_CALL, CHAINED_CALL, GLOBAL_CALL)

    private fun kindForCall(fn: String): SymbolKind? = when (fn) {
        "route", "route_is" -> SymbolKind.ROUTE
        "view" -> SymbolKind.VIEW
        "config" -> SymbolKind.CONFIG
        "__", "trans" -> SymbolKind.TRANSLATION
        "env" -> SymbolKind.ENV
        "can", "authorize" -> SymbolKind.GATE
        "middleware" -> SymbolKind.MIDDLEWARE
        "disk" -> SymbolKind.DISK
        "render" -> SymbolKind.INERTIA
        "table" -> SymbolKind.TABLE
        "vite", "asset", "url" -> SymbolKind.VITE
        else -> when {
            fn in RELATION_FN_SET -> SymbolKind.RELATION
            fn in COLUMN_FN_SET -> SymbolKind.COLUMN
            else -> null
        }
    }

    /**
     * @param dotenvFile when true, also match bare ``KEY`` / ``KEY=value`` lines
     *   (only safe inside ``.env`` / ``.env.*`` files).
     */
    fun detect(beforeCaret: String, dotenvFile: Boolean = false): Site? {
        if (dotenvFile) {
            detectDotenvLine(beforeCaret.substringAfterLast('\n'))?.let { return it }
        }

        val text = beforeCaret.replace('\n', ' ')
        val tail = if (text.length > 320) text.takeLast(320) else text

        DOTENV_INTERPOLATION.find(tail)?.let {
            return Site(SymbolKind.ENV, it.groups["pre"]?.value ?: "")
        }
        ENV_DEFAULT.find(tail)?.let { m ->
            val pre = m.groups["pre"]?.value ?: m.groups["bare"]?.value ?: ""
            return Site(SymbolKind.ENV_VALUE, pre, receiver = m.groups["key"]?.value)
        }
        COMPONENT_TAG.find(tail)?.let {
            return Site(SymbolKind.COMPONENT, it.groups["pre"]?.value ?: "")
        }
        AT_DIRECTIVE.find(tail)?.let {
            if (!tail.contains("(") || tail.lastIndexOf('@') > tail.lastIndexOf('(')) {
                return Site(SymbolKind.DIRECTIVE, it.groups["pre"]?.value ?: "")
            }
        }
        DIRECTIVE_VIEW.find(tail)?.let { m ->
            val name = m.value.substringAfter("@").substringBefore("(").trim()
            val kind = when (name) {
                "extends", "include", "includeIf", "includeWhen", "includeUnless", "each" ->
                    SymbolKind.VIEW
                "component" -> SymbolKind.COMPONENT
                "lang", "choice" -> SymbolKind.TRANSLATION
                "can", "cannot", "canany", "cannotany" -> SymbolKind.GATE
                "route", "signedRoute" -> SymbolKind.ROUTE
                "asset", "vite" -> SymbolKind.VITE
                else -> SymbolKind.VIEW // defensive — regex only matches known names
            }
            return Site(kind, m.groups["pre"]?.value ?: "")
        }
        CASTS_VALUE.find(tail)?.let {
            return Site(SymbolKind.CAST, it.groups["pre"]?.value ?: "")
        }
        CASTS_KEY.find(tail)?.let {
            return Site(SymbolKind.MODEL_ATTR, it.groups["pre"]?.value ?: "", receiver = "casts")
        }
        MODEL_LIST.find(tail)?.let { m ->
            return Site(
                SymbolKind.MODEL_ATTR,
                m.groups["pre"]?.value ?: "",
                receiver = m.groups["attr"]?.value,
            )
        }
        CONTROLLER_ACTION.find(tail)?.let {
            return Site(SymbolKind.CONTROLLER_ACTION, it.groups["pre"]?.value ?: "")
        }
        SMITH.find(tail)?.let {
            return Site(SymbolKind.SMITH, it.groups["pre"]?.value ?: "")
        }
        VALIDATION.find(tail)?.let { m ->
            val pre = m.groups["pre"]?.value ?: ""
            val segment = pre.substringAfterLast("|", pre)
            val prefix = if (pre.contains("|")) segment else pre
            return Site(SymbolKind.VALIDATION, prefix, validationSegment = pre.contains("|"))
        }
        KWARG_COLUMN.find(tail)?.let { m ->
            return Site(
                SymbolKind.COLUMN,
                m.groups["pre"]?.value ?: "",
                receiver = m.groups["recv"]?.value,
            )
        }
        DICT_COLUMN.find(tail)?.let { m ->
            return Site(
                SymbolKind.COLUMN,
                m.groups["pre"]?.value ?: "",
                receiver = m.groups["recv"]?.value,
            )
        }
        MODEL_QUERY_CHAIN.find(tail)?.let { m ->
            val fn = m.named("fn")?.lowercase() ?: return@let
            val kind = kindForCall(fn) ?: return@let
            return Site(kind, m.named("pre") ?: "", receiver = m.named("recv"))
        }
        CALL_MATCHERS.forEach { regex ->
            regex.find(tail)?.let { m ->
                val fn = m.named("fn")?.lowercase() ?: return@let
                val pre = m.named("pre") ?: ""
                var recv = m.named("recv")
                val kind = kindForCall(fn) ?: return@let
                // ``).where("`` after a model query chain — recover Author from earlier text.
                if (recv == null && (kind == SymbolKind.COLUMN || kind == SymbolKind.RELATION)) {
                    recv = AlmasixModelResolver.inferChainHead(tail)
                }
                return Site(kind, pre, receiver = recv)
            }
        }
        ATTR.find(tail)?.let { m ->
            val pre = m.named("pre") ?: ""
            val recv = when {
                m.value.contains("auth") && m.value.contains("user") ->
                    AlmasixModelResolver.AUTH_USER_SENTINEL
                m.value.contains("request") && m.value.contains("user") ->
                    AlmasixModelResolver.AUTH_USER_SENTINEL
                else -> m.named("recv")
            }
            // Avoid eating ``route("x`` style — CALL already handled quoted forms.
            if (recv != null && recv !in setOf("route", "view", "config", "env")) {
                return Site(SymbolKind.ATTR, pre, receiver = recv)
            }
        }
        Regex("""\{(?:\{|!!)\s*(?<pre>[A-Za-z_][\w.]*)?\z""").find(tail)?.let {
            return Site(SymbolKind.TEMPLATE_VAR, it.groups["pre"]?.value ?: "")
        }
        return null
    }

    private fun MatchResult.named(name: String): String? =
        try {
            groups[name]?.value
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun detectDotenvLine(line: String): Site? {
        if (line.lstrip().startsWith("#")) return null
        DOTENV_INTERPOLATION.find(line)?.let {
            return Site(SymbolKind.ENV, it.groups["pre"]?.value ?: "")
        }
        DOTENV_VALUE.matchEntire(line)?.let { m ->
            val raw = m.groups["pre"]?.value ?: ""
            return Site(
                SymbolKind.ENV_VALUE,
                stripDotenvValuePrefix(raw),
                receiver = m.groups["key"]?.value,
            )
        }
        DOTENV_KEY.matchEntire(line)?.let { m ->
            return Site(SymbolKind.ENV, m.groups["pre"]?.value ?: "")
        }
        return null
    }

    private fun String.lstrip(): String = trimStart()

    private fun stripDotenvValuePrefix(raw: String): String {
        val text = raw.trimStart()
        if (text.isEmpty()) return ""
        if (text[0] == '"' || text[0] == '\'') {
            val q = text[0]
            if (text.length == 1) return ""
            return if (text.last() == q) text.substring(1, text.length - 1) else text.substring(1)
        }
        return text
    }
}
