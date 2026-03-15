package com.example.hyperisland.xposed

import com.example.hyperisland.xposed.templates.GenericProgressIslandNotification

/**
 * 可供 Flutter 读取的模板元数据列表（无 Xposed 依赖）。
 *
 * 新增模板时，在此添加一行；模板名称来自各模板文件的 const 常量，
 * 编译器会将其内联，不会在普通 App 进程中触发 Xposed 类加载。
 */
val registeredTemplates: List<Map<String, String>> = listOf(
    mapOf(
        "id"   to GenericProgressIslandNotification.TEMPLATE_ID,
        "name" to GenericProgressIslandNotification.TEMPLATE_NAME,
    ),
)
