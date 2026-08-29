package io.github.hyperisland.compose.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream

internal object RestartScopeService {
    suspend fun restart(commands: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val process = Runtime.getRuntime().exec("su")
            DataOutputStream(process.outputStream).use { writer ->
                commands.forEach { command -> writer.writeBytes("$command\n") }
                writer.writeBytes("exit\n")
                writer.flush()
            }
            val exitCode = process.waitFor()
            check(exitCode == 0) { "Root permission denied (exit $exitCode)" }
        }
    }
}
