package io.github.hyperisland.xposed.hook.SystemUI.extensions

import android.content.Context
import android.graphics.drawable.Icon
import android.media.session.MediaSession
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.hyperisland.xposed.hook.SystemUI.extensions.spectrum.ArtworkPalette
import io.github.hyperisland.xposed.hook.SystemUI.extensions.spectrum.MediaRoute
import io.github.hyperisland.xposed.hook.SystemUI.extensions.spectrum.MusicSpectrum
import io.github.hyperisland.xposed.hook.SystemUI.extensions.spectrum.SpectrumView
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.util.Collections
import java.util.WeakHashMap

object MusicIslandSpectrumHook : BaseHook() {
    private const val HOLDER = "miui.systemui.dynamicisland.module.IslandIconViewHolder"
    private const val CONTROLLER =
        "com.android.systemui.statusbar.notification.mediaisland.MiuiIslandMediaControllerImpl"
    private val hooked = Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    private val main = Handler(Looper.getMainLooper())

    override fun getTag() = "HyperIsland[MusicSpectrum]"

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        MusicSpectrum.reporter = { message, failure ->
            if (failure == null) log(module) { message }
            else logWarn(module, "$message: ${failure.javaClass.simpleName}: ${failure.message}")
        }
        install(module, param.defaultClassLoader)
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { install(module, it) }
    }

    private fun install(module: XposedModule, loader: ClassLoader) {
        installClass(module, loader, CONTROLLER, ::hookMedia)
        installClass(module, loader, HOLDER, ::hookHolder)
    }

    private fun installClass(
        module: XposedModule,
        loader: ClassLoader,
        name: String,
        hook: (XposedModule, Class<*>) -> Unit,
    ) {
        runCatching {
            val type = Class.forName(name, false, loader)
            synchronized(hooked) {
                if (hooked.add(type)) hook(module, type)
            }
        }.onFailure {
            if (it !is ClassNotFoundException) logWarn(module, "$name: ${it.message}")
        }
    }

    private fun hookMedia(module: XposedModule, type: Class<*>) {
        val methods = type.declaredMethods
        val updates = methods.filter {
            it.parameterCount == 3 && when (it.name) {
                "addDynamicIslandView" -> it.parameterTypes[2].simpleName == "MediaData"
                "generateRightImage" -> it.parameterTypes[1].simpleName == "MediaData"
                else -> false
            }
        }
        if (updates.isEmpty()) return
        val castDevice = type.field("castDeviceType")
        val context = type.field("context")
        for (method in updates) {
            val mediaIndex = if (method.name == "generateRightImage") 1 else 2
            val media = method.parameterTypes[mediaIndex]
            val pkg = media.field("packageName") ?: continue
            val user = media.field("userId") ?: continue
            val key = media.field("notificationKey")
            val playing = media.field("isPlaying")
            val token = media.field("token")
            val artwork = media.field("artwork")
            val mediaId = media.field("mediaId")
            module.hook(method).intercept { chain ->
                val result = chain.proceed()
                runCatching {
                    val controller = chain.thisObject
                    val data = chain.args[mediaIndex] ?: return@runCatching
                    val ctx = context?.get(controller) as? Context ?: return@runCatching
                    val name = pkg.get(data) as? String ?: return@runCatching
                    val userId = user.getInt(data)
                    val notificationKey = key?.get(data) as? String
                    val isPlaying = playing?.get(data) as? Boolean
                    val route = castDevice?.get(controller)?.toString().orEmpty()
                    val session = token?.get(data) as? MediaSession.Token
                    val cover = artwork?.get(data) as? Icon
                    val coverTrack = "$name|$userId|$notificationKey|${mediaId?.get(data)}"
                    onMain {
                        ArtworkPalette.update(ctx, coverTrack, cover)
                        MusicSpectrum.media(ctx, notificationKey, name, userId, isPlaying, route, session)
                    }
                }
                result
            }
        }
        methods.filter {
            it.name.startsWith("removeDynamicIslandView") &&
                it.parameterTypes.contentEquals(arrayOf(String::class.java, Boolean::class.javaPrimitiveType))
        }.forEach { method ->
            module.hook(method).intercept { chain ->
                val result = chain.proceed()
                (chain.args.firstOrNull() as? String)?.let { key -> onMain { MusicSpectrum.removed(key) } }
                result
            }
        }
    }

    private fun hookHolder(module: XposedModule, type: Class<*>) {
        val containerField = type.field("iconContainer") ?: return
        val lottieField = type.field("lottieView") ?: return
        val fixedField = type.field("fixIcon")
        val picInfo = type.field("picInfo")
        val getPicInfo = type.methods.firstOrNull { it.name == "getPicInfo" && it.parameterCount == 0 }
        val top = type.field("gradientTopColor")
        val bottom = type.field("gradientBottomColor")

        for (method in type.declaredMethods) {
            val event = method.name
            val bind = (event == "bind" || event == "updatePartial") && method.parameterCount == 2
            val lifecycle = method.parameterCount == 0 &&
                event in setOf("onShow", "onHidden", "onDetach", "playAnimation", "registerLottieCallback")
            if (!bind && !lifecycle) continue
            module.hook(method).intercept { chain ->
                val result = chain.proceed()
                val holder = chain.thisObject?.let(::WeakReference)
                onMain {
                    runCatching {
                        val instance = holder?.get() ?: return@runCatching
                        val container = containerField.get(instance) as? FrameLayout ?: return@runCatching
                        val existing = (0 until container.childCount).firstNotNullOfOrNull {
                            container.getChildAt(it) as? SpectrumView
                        }
                        when (event) {
                            "onDetach" -> existing?.dispose()
                            "onHidden" -> existing?.allow(false)
                            "playAnimation", "registerLottieCallback" -> existing?.suppressOriginal()
                            else -> {
                                val info = getPicInfo?.invoke(instance) ?: picInfo?.get(instance)
                                val pic = info?.javaClass?.getMethod("getPic")?.invoke(info) as? String
                                val wired = pic == "miui_music_cast_others" && MusicSpectrum.wired
                                val waveform = pic == "musicWave" || pic == "musicPause"
                                if (!wired && !waveform) {
                                    existing?.dispose()
                                    return@runCatching
                                }
                                val original = (if (wired) fixedField else lottieField)?.get(instance) as? View
                                if (original?.parent !== container) {
                                    existing?.dispose()
                                    return@runCatching
                                }
                                val layout = original.layoutParams as? FrameLayout.LayoutParams ?: return@runCatching
                                val view = existing ?: SpectrumView(container.context) {
                                    val owner = holder.get()
                                    SpectrumView.CoverColors(top.color(owner), bottom.color(owner))
                                }
                                view.bind(original, pic == "musicWave", wired)
                                if (view.parent == null) container.addView(view, FrameLayout.LayoutParams(layout))
                                if (event == "onShow") view.allow(true)
                            }
                        }
                    }.onFailure { MusicSpectrum.reportOnce("holder_$event", it) }
                }
                result
            }
        }
    }

    private fun Field?.color(holder: Any?): Int =
        runCatching { this?.getInt(holder) ?: -1 }.getOrDefault(-1)

    private fun Class<*>.field(name: String): Field? {
        var owner: Class<*>? = this
        while (owner != null) {
            val field = runCatching { owner.getDeclaredField(name) }.getOrNull()
            if (field != null) return field.apply { isAccessible = true }
            owner = owner.superclass
        }
        return null
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == main.looper) action() else main.post { action() }
    }
}
