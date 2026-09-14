package com.almasix.ide

import com.intellij.execution.util.ExecUtil
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Spawns `smith ide:index` — thin process shell excluded from the coverage gate.
 */
object AlmasixIndexProcess {
    fun run(root: Path): AlmasixIndex {
        val cmd = AlmasixIndexLoader.buildIndexCommand(root)
        cmd.withWorkDirectory(root.toFile())
        val output = ExecUtil.execAndGetOutput(cmd, TimeUnit.SECONDS.toMillis(60).toInt())
        if (output.exitCode != 0 && output.stdout.isBlank()) {
            return AlmasixIndex.empty(
                error = "ide:index failed (exit ${output.exitCode}): ${output.stderr.take(500)}",
            )
        }
        val text = output.stdout.trim()
        if (text.isEmpty()) {
            return AlmasixIndex.empty(error = "ide:index produced no output: ${output.stderr.take(500)}")
        }
        return AlmasixIndexLoader.parse(text)
    }
}
