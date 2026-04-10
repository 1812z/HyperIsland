package io.github.hyperisland.xposed.template.core.contracts

import android.content.Context
import android.os.Bundle
import io.github.hyperisland.xposed.template.core.models.NotifData

/**
 * 灵动岛通知模板接口。
 *
 * 新增模板步骤：
 *  1. 创建 object 实现此接口，id 与 Flutter 侧常量对应
 *  2. 在 TemplateRegistry.registry 中添加一行
 */
interface IslandTemplate {
    /** 唯一标识符，与 Flutter 侧 kTemplate* 常量对应。 */
    val id: String

    /** 将通知数据注入 extras，使其触发灵动岛展示。 */
    fun inject(context: Context, extras: Bundle, data: NotifData)
}
