package io.github.hyperisland.compose.data

import io.github.hyperisland.core.data.NotificationChannelRepository

internal data class NotificationChannelInfo(
    val id: String,
    val name: String,
    val description: String,
    val importance: Int,
)

internal class NotificationChannelsRepository {
    private val delegate = NotificationChannelRepository("HyperIslandCompose")

    /** null 表示通知策略文件无法读取，通常是 ROOT 未授权。 */
    fun load(packageName: String): List<NotificationChannelInfo>? =
        delegate.getNotificationChannelsForPackage(packageName)?.mapNotNull { channel ->
            val id = channel["id"] as? String ?: return@mapNotNull null
            NotificationChannelInfo(
                id = id,
                name = (channel["name"] as? String).orEmpty().ifEmpty { id },
                description = (channel["description"] as? String).orEmpty(),
                importance = (channel["importance"] as? Number)?.toInt() ?: -1,
            )
        }
}
