package com.almasix.ide

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.execution.configurations.GeneralCommandLine
import java.nio.file.Files
import java.nio.file.Path

/**
 * Parses `smith ide:index --json` dumps and builds the index command line.
 * Process spawning lives in [AlmasixIndexProcess].
 */
object AlmasixIndexLoader {
    fun parse(json: String): AlmasixIndex {
        val root = JsonParser.parseString(json).asJsonObject
        val routes = mutableMapOf<String, AlmasixIndex.RouteEntry>()
        root.getAsJsonObject("routes")?.entrySet()?.forEach { (name, value) ->
            val obj = value.asJsonObject
            routes[name] = AlmasixIndex.RouteEntry(
                uri = stringOrEmpty(obj, "uri"),
                methods = obj.getAsJsonArray("methods")?.map { it.asString } ?: emptyList(),
                path = stringOrNull(obj, "path"),
                line = obj.get("line")?.asInt ?: 0,
            )
        }
        val tables = mutableMapOf<String, AlmasixIndex.TableEntry>()
        root.getAsJsonObject("tables")?.entrySet()?.forEach { (name, value) ->
            val obj = value.asJsonObject
            val cols = mutableMapOf<String, AlmasixIndex.Located>()
            obj.getAsJsonObject("columns")?.entrySet()?.forEach { (colName, colVal) ->
                val col = colVal.asJsonObject
                cols[colName] = AlmasixIndex.Located(
                    path = stringOrNull(col, "path"),
                    line = col.get("line")?.asInt ?: 0,
                )
            }
            tables[name] = AlmasixIndex.TableEntry(
                columns = cols,
                detail = stringOrEmpty(obj, "detail"),
                path = stringOrNull(obj, "path"),
                line = obj.get("line")?.asInt ?: 0,
                model = stringOrNull(obj, "model"),
            )
        }
        val modelMetadata = mutableMapOf<String, AlmasixIndex.ModelEntry>()
        val relations = mutableMapOf<String, List<String>>()
        root.getAsJsonObject("model_metadata")?.entrySet()?.forEach { (name, value) ->
            val obj = value.asJsonObject
            val rels = obj.getAsJsonArray("relations")?.map { it.asString } ?: emptyList()
            val casts = mutableMapOf<String, String>()
            obj.getAsJsonObject("casts")?.entrySet()?.forEach { (k, v) -> casts[k] = v.asString }
            val relationLines = mutableMapOf<String, Int>()
            obj.getAsJsonObject("relation_lines")?.entrySet()?.forEach { (k, v) ->
                relationLines[k] = v.asInt
            }
            modelMetadata[name] = AlmasixIndex.ModelEntry(
                fillable = obj.getAsJsonArray("fillable")?.map { it.asString } ?: emptyList(),
                guarded = obj.getAsJsonArray("guarded")?.map { it.asString } ?: emptyList(),
                hidden = obj.getAsJsonArray("hidden")?.map { it.asString } ?: emptyList(),
                casts = casts,
                relations = rels,
                relationLines = relationLines,
                module = stringOrEmpty(obj, "module"),
                path = stringOrEmpty(obj, "path"),
            )
            relations[name] = rels
        }
        root.getAsJsonObject("relations")?.entrySet()?.forEach { (name, value) ->
            relations[name] = value.asJsonArray.map { it.asString }
        }

        val viewData = mutableMapOf<String, Map<String, AlmasixIndex.ViewVarEntry>>()
        root.getAsJsonObject("view_data")?.entrySet()?.forEach { (view, value) ->
            val vars = mutableMapOf<String, AlmasixIndex.ViewVarEntry>()
            value.asJsonObject.entrySet().forEach { (varName, varVal) ->
                vars[varName] = viewVarEntry(varVal.asJsonObject)
            }
            viewData[view] = vars
        }

        val viewHelpers = mutableMapOf<String, AlmasixIndex.ViewVarEntry>()
        root.getAsJsonArray("view_helpers")?.forEach { el ->
            if (!el.isJsonObject) return@forEach
            val obj = el.asJsonObject
            val name = obj.get("name")?.asString ?: return@forEach
            viewHelpers[name] = viewVarEntry(obj)
        }

        val viewShared = mutableMapOf<String, AlmasixIndex.ViewVarEntry>()
        root.getAsJsonObject("view_shared")?.entrySet()?.forEach { (name, value) ->
            if (value.isJsonObject) {
                viewShared[name] = viewVarEntry(value.asJsonObject)
            } else {
                viewShared[name] = AlmasixIndex.ViewVarEntry(kind = "shared")
            }
        }

        val controllerActions = mutableMapOf<String, List<String>>()
        root.getAsJsonObject("controller_actions")?.entrySet()?.forEach { (name, value) ->
            controllerActions[name] = value.asJsonArray.map { it.asString }
        }

        val envKeys = mutableMapOf<String, AlmasixIndex.EnvEntry>()
        root.getAsJsonObject("env_keys")?.entrySet()?.forEach { (name, value) ->
            val obj = value.asJsonObject
            val usedBy = obj.getAsJsonArray("used_by")?.map { it.asString } ?: emptyList()
            envKeys[name] = AlmasixIndex.EnvEntry(
                path = stringOrNull(obj, "path"),
                line = obj.get("line")?.asInt ?: 0,
                kind = stringOrEmpty(obj, "kind"),
                detail = stringOrEmpty(obj, "detail"),
                usedBy = usedBy,
            )
        }

        val envOptions = mutableMapOf<String, List<String>>()
        root.getAsJsonObject("env_options")?.entrySet()?.forEach { (name, value) ->
            envOptions[name] = when {
                value.isJsonArray -> value.asJsonArray.map { it.asString }
                else -> emptyList()
            }
        }

        val configLocations = mutableMapOf<String, AlmasixIndex.Located>()
        root.getAsJsonObject("config_locations")?.entrySet()?.forEach { (name, value) ->
            val obj = value.asJsonObject
            configLocations[name] = AlmasixIndex.Located(
                path = stringOrNull(obj, "path"),
                line = obj.get("line")?.asInt ?: 0,
            )
        }

        return AlmasixIndex(
            basePath = stringOrNull(root, "base_path") ?: "",
            ok = root.get("ok")?.asBoolean ?: false,
            error = stringOrNull(root, "error"),
            views = stringMap(root, "views"),
            routes = routes,
            configKeys = stringList(root, "config_keys"),
            configFiles = stringMap(root, "config_files"),
            configLocations = configLocations,
            translationKeys = stringList(root, "translation_keys"),
            middlewareAliases = stringList(root, "middleware_aliases"),
            envKeys = envKeys,
            envOptions = envOptions,
            tables = tables,
            modelMetadata = modelMetadata,
            relations = relations,
            casts = stringList(root, "casts"),
            components = stringMap(root, "components"),
            gates = stringList(root, "gates"),
            disks = stringList(root, "disks"),
            queues = stringList(root, "queues"),
            caches = stringList(root, "caches"),
            mailers = stringList(root, "mailers"),
            inertiaPages = stringList(root, "inertia_pages"),
            smithCommands = stringList(root, "smith_commands"),
            validationRules = stringList(root, "validation_rules"),
            directives = stringList(root, "directives"),
            viewHelpers = viewHelpers,
            viewShared = viewShared,
            viewData = viewData,
            viteEntries = stringMap(root, "vite_entries"),
            controllerActions = controllerActions,
        )
    }

    internal fun buildIndexCommand(root: Path): GeneralCommandLine {
        val venvPython = root.resolve(".venv/bin/python")
        val venvSmith = root.resolve(".venv/bin/smith")
        val smithScript = root.resolve("smith")
        return when {
            Files.isExecutable(venvSmith) ->
                GeneralCommandLine(venvSmith.toString(), "ide:index", "--json")
            Files.isExecutable(venvPython) && Files.isRegularFile(smithScript) ->
                GeneralCommandLine(venvPython.toString(), smithScript.toString(), "ide:index", "--json")
            Files.isExecutable(venvPython) ->
                GeneralCommandLine(venvPython.toString(), "-m", "almasix.ide", "--path", root.toString())
            Files.isRegularFile(smithScript) ->
                GeneralCommandLine("python3", smithScript.toString(), "ide:index", "--json")
            else ->
                GeneralCommandLine("smith", "ide:index", "--json")
        }
    }

    private fun viewVarEntry(obj: JsonObject): AlmasixIndex.ViewVarEntry =
        AlmasixIndex.ViewVarEntry(
            path = stringOrNull(obj, "path"),
            line = obj.get("line")?.asInt ?: 0,
            kind = stringOrEmpty(obj, "kind").ifBlank { "data" },
        )

    private fun stringOrNull(obj: JsonObject, field: String): String? {
        val el = obj.get(field) ?: return null
        if (el.isJsonNull) return null
        return el.asString
    }

    private fun stringOrEmpty(obj: JsonObject, field: String): String =
        stringOrNull(obj, field) ?: ""

    private fun stringMap(root: JsonObject, field: String): Map<String, String> {
        val obj = root.getAsJsonObject(field) ?: return emptyMap()
        return obj.entrySet().associate { (k, v) ->
            k to if (v.isJsonPrimitive) v.asString else v.toString()
        }
    }

    private fun stringList(root: JsonObject, field: String): Set<String> {
        val el = root.get(field) ?: return emptySet()
        return when {
            el.isJsonArray -> el.asJsonArray.map { it.asString }.toSet()
            el.isJsonObject -> el.asJsonObject.keySet()
            else -> emptySet()
        }
    }
}
