package io.github.hyperisland.compose.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import io.github.hyperisland.data.IslandPerformanceContract as Contract
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal object IslandPerformanceClient {
    suspend fun request(context: Context, command: String): JSONObject = withTimeout(5_000L) {
        suspendCancellableCoroutine { continuation ->
            val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    if (!continuation.isActive) return
                    if (resultCode != 0) {
                        continuation.resumeWithException(IllegalStateException(resultData?.getString(Contract.ERROR)))
                        return
                    }
                    runCatching { JSONObject(resultData?.getString(Contract.REPORT) ?: error("empty report")) }
                        .onSuccess { continuation.resume(it) }
                        .onFailure { continuation.resumeWithException(it) }
                }
            }
            context.sendBroadcast(
                Intent(Contract.ACTION).setPackage("com.android.systemui")
                    .putExtra(Contract.COMMAND, command).putExtra(Contract.REPLY, receiver),
            )
        }
    }
}
