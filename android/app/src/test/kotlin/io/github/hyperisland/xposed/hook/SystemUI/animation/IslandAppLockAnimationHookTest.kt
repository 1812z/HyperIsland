package io.github.hyperisland.xposed.hook.SystemUI.animation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandAppLockAnimationHookTest {
    @Test
    fun openingAndBringingNativeAppLockToFrontAreEligible() {
        assertTrue(isOpening(OPEN, SECURITY_CENTER, APP_LOCK, SECURITY_CENTER, APP_LOCK))
        assertTrue(isOpening(TO_FRONT, SECURITY_CENTER, CONFIRM_ACCESS_CONTROL, null, null))
    }

    @Test
    fun closingOrHidingAppLockIsIneligible() {
        assertFalse(isOpening(CLOSE, SECURITY_CENTER, APP_LOCK, SECURITY_CENTER, APP_LOCK))
        assertFalse(isOpening(TO_BACK, SECURITY_CENTER, APP_LOCK, SECURITY_CENTER, APP_LOCK))
    }

    @Test
    fun otherActivitiesAreIneligibleEvenIfAnAppLockComponentAppearsInTheTask() {
        assertFalse(isOpening(OPEN, SECURITY_CENTER, APP_LOCK, SECURITY_CENTER, SECURITY_CENTER_HOME))
        assertFalse(isOpening(OPEN, SECURITY_CENTER, APP_LOCK, "com.example.app", APP_LOCK))
        assertFalse(isOpening(OPEN, "com.example.app", APP_LOCK, SECURITY_CENTER, APP_LOCK))
        assertFalse(isOpening(OPEN, SECURITY_CENTER, SECURITY_CENTER_HOME, SECURITY_CENTER, APP_LOCK))
        assertFalse(isOpening(OPEN, SECURITY_CENTER, "${APP_LOCK}Lookalike", null, null))
    }

    @Test
    fun requiresTheOriginalLaunchPackageAndUser() {
        assertTrue(isOpening())
        assertFalse(isOpening(launchPackage = "com.android.systemui"))
        assertFalse(isOpening(behindAppLockPackage = "com.example.other"))
        assertFalse(isOpening(behindAppLockPackage = null))
        assertFalse(isOpening(launchPackage = " ", behindAppLockPackage = " "))
        assertFalse(isOpening(taskUser = 10))
        assertTrue(isOpening(launchUser = 10, taskUser = 10))
    }

    private fun isOpening(
        mode: Int = OPEN,
        basePackage: String? = SECURITY_CENTER,
        baseClass: String? = APP_LOCK,
        topPackage: String? = SECURITY_CENTER,
        topClass: String? = APP_LOCK,
        launchPackage: String = LOCKED_APP,
        launchUser: Int = 0,
        behindAppLockPackage: String? = LOCKED_APP,
        taskUser: Int = 0,
    ): Boolean = isIslandAppLockOpening(
        mode,
        basePackage,
        baseClass,
        topPackage,
        topClass,
        launchPackage,
        launchUser,
        behindAppLockPackage,
        taskUser,
    )

    private companion object {
        const val OPEN = 1
        const val CLOSE = 2
        const val TO_FRONT = 3
        const val TO_BACK = 4
        const val SECURITY_CENTER = "com.miui.securitycenter"
        const val APP_LOCK = "com.miui.applicationlock.AppLockActivity"
        const val CONFIRM_ACCESS_CONTROL = "com.miui.applicationlock.ConfirmAccessControl"
        const val SECURITY_CENTER_HOME = "com.miui.securitycenter.MainActivity"
        const val LOCKED_APP = "com.tencent.mm"
    }
}
