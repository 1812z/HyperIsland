package io.github.hyperisland.utils

import android.app.Activity
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

object InteractionHaptics {
    fun performButton(activity: Activity): Boolean {
        return performHyperHaptic(
            activity = activity,
            preferredConstants = listOf(
                "MIUI_TAP_NORMAL",
                "FLAG_MIUI_HAPTIC_TAP_NORMAL",
                "MIUI_BUTTON_MIDDLE"
            ),
            fallback = HapticFeedbackConstants.KEYBOARD_TAP
        )
    }

    fun performToggle(activity: Activity): Boolean {
        return performHyperHaptic(
            activity = activity,
            preferredConstants = listOf(
                "MIUI_SWITCH",
                "FLAG_MIUI_HAPTIC_SWITCH",
                "MIUI_TAP_LIGHT"
            ),
            fallback = HapticFeedbackConstants.VIRTUAL_KEY
        )
    }

    fun performSliderTick(activity: Activity): Boolean {
        return performHyperHaptic(
            activity = activity,
            preferredConstants = listOf(
                "MIUI_MESH_NORMAL",
                "FLAG_MIUI_HAPTIC_MESH_NORMAL",
                "MIUI_SCROLL_EDGE"
            ),
            fallback = HapticFeedbackConstants.CLOCK_TICK
        )
    }

    private fun performHyperHaptic(
        activity: Activity,
        preferredConstants: List<String>,
        fallback: Int
    ): Boolean {
        val view = activity.window?.decorView ?: return false
        val miuiConstant = resolveMiuiConstant(preferredConstants)
        if (miuiConstant != null) {
            if (invokeMiuixCompat(view, miuiConstant)) return true
            if (view.performHapticFeedback(miuiConstant)) return true
        }
        return performFallback(view, fallback)
    }

    private fun resolveMiuiConstant(names: List<String>): Int? {
        val classes = listOf(
            "miuix.view.HapticFeedbackConstants",
            "miui.view.MiuiHapticFeedbackConstants"
        )
        for (className in classes) {
            try {
                val clazz = Class.forName(className)
                for (name in names) {
                    try {
                        return clazz.getField(name).getInt(null)
                    } catch (_: Throwable) {
                    }
                }
            } catch (_: Throwable) {
            }
        }
        return null
    }

    private fun invokeMiuixCompat(view: View, feedbackConstant: Int): Boolean {
        val classes = listOf(
            "miuix.view.HapticCompat",
            "miui.view.HapticCompat"
        )
        for (className in classes) {
            try {
                val clazz = Class.forName(className)
                val method = clazz.methods.firstOrNull { candidate ->
                    candidate.name == "performHapticFeedback" &&
                        candidate.parameterTypes.size >= 2 &&
                        View::class.java.isAssignableFrom(candidate.parameterTypes[0]) &&
                        candidate.parameterTypes[1] == Int::class.javaPrimitiveType
                } ?: continue
                val args = when (method.parameterTypes.size) {
                    2 -> arrayOf(view, feedbackConstant)
                    3 -> arrayOf(view, feedbackConstant, false)
                    else -> continue
                }
                val result = method.invoke(null, *args)
                if (result !is Boolean || result) return true
            } catch (_: Throwable) {
            }
        }
        return false
    }

    private fun performFallback(view: View, constant: Int): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(constant)
            } else {
                @Suppress("DEPRECATION")
                view.performHapticFeedback(constant)
            }
        } catch (_: Throwable) {
            false
        }
    }
}
