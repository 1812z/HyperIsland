package io.github.hyperisland.xposed.islanddispatch.definition

object IslandDispatchContract {
    const val ACTION = "io.github.hyperisland.ACTION_SHOW_ISLAND"
    const val ACTION_CANCEL = "io.github.hyperisland.ACTION_CANCEL_ISLAND"
    const val EXTRA_NOTIF_ID = "notif_id"

    const val PERM = "io.github.hyperisland.SEND_ISLAND"
    const val NOTIF_ID = 0x48594944

    const val CHANNEL_ID = "hyperisland_dispatcher"
    const val CHANNEL_NAME = "HyperIsland"
    const val TAG = "HyperIsland[Dispatcher]"
}
