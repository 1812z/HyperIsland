package io.github.hyperisland.compose.service

import android.content.Context
import io.github.hyperisland.XposedPrefsSyncApp

/** Reusable app-side access to LSPosed scope management. */
object XposedScopeService {
    fun requestScope(context: Context, packages: List<String>): Result<Unit> = runCatching {
        application(context).requestScope(normalize(packages))
    }

    fun requestScope(
        context: Context,
        packages: List<String>,
        onResult: (Result<List<String>>) -> Unit,
    ) {
        val application = runCatching { application(context) }.getOrElse {
            onResult(Result.failure(it))
            return
        }
        runCatching {
            application.requestScope(normalize(packages), onResult)
        }.onFailure {
            onResult(Result.failure(it))
        }
    }

    fun currentScope(context: Context): Result<List<String>> = runCatching {
        application(context).getCurrentScope()
    }

    private fun application(context: Context): XposedPrefsSyncApp =
        context.applicationContext as? XposedPrefsSyncApp
            ?: error("HyperIsland application is unavailable")

    private fun normalize(packages: List<String>): List<String> =
        packages.asSequence().map(String::trim).filter(String::isNotEmpty).distinct().toList()
}
