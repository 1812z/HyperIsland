package io.github.hyperisland.xposed.islanddispatch

import android.content.Context
import io.github.hyperisland.xposed.islanddispatch.broadcast.IslandDispatcherBroadcaster
import io.github.hyperisland.xposed.islanddispatch.core.IslandDispatchState
import io.github.hyperisland.xposed.islanddispatch.definition.IslandDispatchContract
import io.github.hyperisland.xposed.islanddispatch.definition.IslandRequest
import io.github.hyperisland.xposed.islanddispatch.invoke.IslandDispatcherNotifier
import io.github.hyperisland.xposed.islanddispatch.receiver.IslandDispatcherReceiver
import io.github.hyperisland.xposed.log
import io.github.libxposed.api.XposedModule

object IslandDispatcher {

    const val ACTION = IslandDispatchContract.ACTION
    const val ACTION_CANCEL = IslandDispatchContract.ACTION_CANCEL
    const val EXTRA_NOTIF_ID = IslandDispatchContract.EXTRA_NOTIF_ID
    const val PERM = IslandDispatchContract.PERM
    const val NOTIF_ID = IslandDispatchContract.NOTIF_ID
    const val CHANNEL_ID = IslandDispatchContract.CHANNEL_ID

    private val registerLock = Any()

    fun register(context: Context, xposedModule: XposedModule) {
        if (IslandDispatchState.registered) return
        synchronized(registerLock) {
            if (IslandDispatchState.registered) return
            val appCtx = context.applicationContext ?: context
            IslandDispatchState.module = xposedModule
            IslandDispatcherNotifier.ensureChannel(appCtx)
            IslandDispatcherReceiver.register(appCtx)
            IslandDispatchState.registered = true
            xposedModule.log("${IslandDispatchContract.TAG}: registered in pid=${android.os.Process.myPid()}")
        }
    }

    fun post(context: Context, request: IslandRequest) {
        IslandDispatcherNotifier.post(context, request)
    }

    fun sendBroadcast(context: Context, request: IslandRequest) {
        IslandDispatcherBroadcaster.send(context, request)
    }

    fun cancel(context: Context, notifId: Int) {
        IslandDispatcherNotifier.cancel(context, notifId)
    }
}
