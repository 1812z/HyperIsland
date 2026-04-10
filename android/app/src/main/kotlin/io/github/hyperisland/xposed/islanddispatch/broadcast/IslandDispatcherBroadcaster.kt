package io.github.hyperisland.xposed.islanddispatch.broadcast

import android.content.Context
import android.content.Intent
import io.github.hyperisland.xposed.islanddispatch.definition.IslandDispatchContract
import io.github.hyperisland.xposed.islanddispatch.definition.IslandRequest

internal object IslandDispatcherBroadcaster {
    fun send(context: Context, request: IslandRequest) {
        val intent = Intent(IslandDispatchContract.ACTION).apply {
            putExtras(request.toBundle())
        }
        context.sendBroadcast(intent)
    }
}
