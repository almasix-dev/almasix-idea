package com.almasix.ide

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.util.ExecUtil
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Runs `smith ide:index --json` (or `python -m almasix.ide.index`) and parses the dump.
 */
object AlmasixIndexLoader {
    fun load(root: Path): AlmasixIndex {
        val cmd = buildIndexCommand(root)
        cmd.withWorkDirectory(root.toFile())
        val output: ProcessOutput = ExecUtil.execAndGetOutput(cmd, TimeUnit.SECONDS.toMillis(60).toInt())
        if (output.exitCode != 0 && output.stdout.isBlank()) {
            return AlmasixIndex.empty(
                error = "ide:index failed (exit ${output.exitCode}): ${output.stderr.take(500)}",
            )
        }
        val text = output.stdout.trim()
        if (text.isEmpty()) {
            return AlmasixIndex.empty(error = "ide:index produced no output: ${output.stderr.take(500)}")
        }
        return parse(text)
    }

    fun parse(json: String): AlmasixIndex {
        val root = JsonParser.parseString(json).asJsonObject
        val routes = mutableMapOf<String, AlmasixIndex.RouteEntry>()
        root.getAsJsonObject("routes")?.entrySet()?.forEach { (name, value) ->
            val obj = value.asJsonObject
            routes[name] = AlmasixIndex.RouteEntry(
                uri = obj.get("uri")?.asString ?: "",
                methods = obj.getAsJsonArray("methods")?.map { it.asString } ?: emptyList(),
            )
        }
        val tables = mutableMapOf<String, AlmasixIndex.TableEntry>()
        root.getAsJsonObject("tables")?.entrySet()?.forEach { (name, value) ->
            val obj = value.asJsonObject
            val cols = obj.getAsJsonObject("columns")?.keySet() ?: emptySet()
            tables[name] = AlmasixIndex.TableEntry(
                columns = cols,
                detail = obj.get("detail")?.asString ?: "",
            )
        }
        val modelMetadata = mutableMapOf<String, AlmasixIndex.ModelEntry>()
        val relations = mutableMapOf<String, List<String>>()
        root.getAsJsonObject("model_metadata")?.entrySet()?.forEach { (name, value) ->
            val obj = value.asJsonObject
            val rels = obj.getAsJsonArray("relations")?.map { it.asString } ?: emptyList()
            val casts = mutableMapOf<String, String>()
            obj.getAsJsonObject("casts")?.entrySet()?.forEach { (k, v) -> casts[k] = v.asString }
            modelMetadata[name] = AlmasixIndex.ModelEntry(
                fillable = obj.getAsJsonArray("fillable")?.map { it.asString } ?: emptyList(),
                casts = casts,
                relations = rels,
                module = obj.get("module")?.asString ?: "",
            )
            relations[name] = rels
        }
        root.getAsJsonObject("relations")?.entrySet()?.forEach { (name, value) ->
            relations[name] = value.asJsonArray.map { it.asString }
        }

        val viewData = mutableMapOf<String, Set<String>>()
        root.getAsJsonObject("view_data")?.entrySet()?.forEach { (view, value) ->
            viewData[view] = value.asJsonObject.keySet()
        }

        val controllerActions = mutableMapOf<String, List<String>>()
        root.getAsJsonObject("controller_actions")?.entrySet()?.forEach { (name, value) ->
            controllerActions[name] = value.asJsonArray.map { it.asString }
        }

        return AlmasixIndex(
            basePath = stringOrNull(root, "base_path") ?: "",
            ok = root.get("ok")?.asBoolean ?: false,
            error = stringOrNull(root, "error"),
            views = stringKeys(root, "views"),
            routes = routes,
            configKeys = stringList(root, "config_keys"),
            translationKeys = stringList(root, "translation_keys"),
            middlewareAliases = stringList(root, "middleware_aliases"),
            envKeys = stringKeys(root, "env_keys"),
            tables = tables,
            modelMetadata = modelMetadata,
            relations = relations,
            casts = stringList(root, "casts"),
            components = stringKeys(root, "components"),
            gates = stringList(root, "gates"),
            disks = stringList(root, "disks"),
            queues = stringList(root, "queues"),
            caches = stringList(root, "caches"),
            mailers = stringList(root, "mailers"),
            inertiaPages = stringList(root, "inertia_pages"),
            smithCommands = stringList(root, "smith_commands"),
            validationRules = stringList(root, "validation_rules"),
            directives = stringList(root, "directives"),
            viewHelpers = helperNames(root),
            viewShared = stringKeys(root, "view_shared"),
            viewData = viewData,
            viteEntries = stringKeys(root, "vite_entries"),
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

    private fun stringOrNull(root: JsonObject, field: String): String? {
        val el = root.get(field) ?: return null
        if (el.isJsonNull) return null
        return el.asString
    }

    private fun stringKeys(root: JsonObject, field: String): Set<String> =
        root.getAsJsonObject(field)?.keySet() ?: emptySet()

    private fun stringList(root: JsonObject, field: String): Set<String> {
        val el = root.get(field) ?: return emptySet()
        return when {
            el.isJsonArray -> el.asJsonArray.map { it.asString }.toSet()
            el.isJsonObject -> el.asJsonObject.keySet()
            else -> emptySet()
        }
    }

    private fun helperNames(root: JsonObject): Set<String> {
        val arr = root.getAsJsonArray("view_helpers") ?: return emptySet()
        return arr.mapNotNull { el ->
            if (el.isJsonObject) el.asJsonObject.get("name")?.asString else null
        }.toSet()
    }
}
