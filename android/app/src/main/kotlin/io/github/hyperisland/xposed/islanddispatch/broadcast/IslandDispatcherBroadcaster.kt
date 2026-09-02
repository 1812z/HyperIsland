package io.github.hyperisland.xposed.islanddispatch.broadcast

import android.app.BroadcastOptions
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.hyperisland.xposed.islanddispatch.definition.IslandDispatchContract
import io.github.hyperisland.xposed.islanddispatch.definition.IslandRequest

internal object IslandDispatcherBroadcaster {
    fun send(context: Context, request: IslandRequest) {
        val intent = Intent(IslandDispatchContract.ACTION).apply {
            setPackage("com.android.systemui")
            putExtras(request.toBundle())
        }
        if (Build.VERSION.SDK_INT >= 34) {
            val options = BroadcastOptions.makeBasic()
                .setShareIdentityEnabled(true)
                .toBundle()
            context.sendBroadcast(intent, null, options)
        } else {
            context.sendBroadcast(intent)
        }
    }
}
