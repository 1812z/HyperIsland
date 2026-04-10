package io.github.hyperisland.xposed.islanddispatch.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import io.github.hyperisland.xposed.islanddispatch.core.IslandDispatchState
import io.github.hyperisland.xposed.islanddispatch.definition.IslandDispatchContract
import io.github.hyperisland.xposed.islanddispatch.definition.IslandRequest
import io.github.hyperisland.xposed.islanddispatch.invoke.IslandDispatcherNotifier
import io.github.hyperisland.xposed.log
import io.github.hyperisland.xposed.logError

internal object IslandDispatcherReceiver {

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val appCtx = context.applicationContext ?: context
            when (intent.action) {
                IslandDispatchContract.ACTION -> handleShow(appCtx, intent)
                IslandDispatchContract.ACTION_CANCEL -> handleCancel(appCtx, intent)
            }
        }
    }

    fun register(context: Context) {
        val filter = IntentFilter(IslandDispatchContract.ACTION).apply {
            addAction(IslandDispatchContract.ACTION_CANCEL)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(
                receiver,
                filter,
                IslandDispatchContract.PERM,
                null,
                Context.RECEIVER_EXPORTED,
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter, IslandDispatchContract.PERM, null)
        }
    }

    private fun handleShow(context: Context, intent: Intent) {
        try {
            val request = IslandRequest.fromIntent(intent)
            IslandDispatchState.module?.log("${IslandDispatchContract.TAG}: onReceive title=${request.title}")
            IslandDispatcherNotifier.post(context, request)
        } catch (e: Exception) {
            IslandDispatchState.module?.logError("${IslandDispatchContract.TAG}: onReceive error: ${e.message}")
        }
    }

    private fun handleCancel(context: Context, intent: Intent) {
        try {
            val notifId = intent.getIntExtra(
                IslandDispatchContract.EXTRA_NOTIF_ID,
                IslandDispatchContract.NOTIF_ID,
            )
            IslandDispatcherNotifier.cancel(context, notifId)
        } catch (e: Exception) {
            IslandDispatchState.module?.logError("${IslandDispatchContract.TAG}: onReceive cancel error: ${e.message}")
        }
    }
}
