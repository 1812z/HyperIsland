package io.github.hyperisland.xposed.hook.SystemUI.animation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandAppLockAnimationHookTest {
    @Test
    fun openingAndBringingNativeAppLockToFrontAreEligible() {
        assertTrue(isIslandAppLockOpening(OPEN, SECURITY_CENTER, APP_LOCK, SECURITY_CENTER, APP_LOCK))
        assertTrue(isIslandAppLockOpening(TO_FRONT, SECURITY_CENTER, CONFIRM_ACCESS_CONTROL, null, null))
    }

    @Test
    fun closingOrHidingAppLockIsIneligible() {
        assertFalse(isIslandAppLockOpening(CLOSE, SECURITY_CENTER, APP_LOCK, SECURITY_CENTER, APP_LOCK))
        assertFalse(isIslandAppLockOpening(TO_BACK, SECURITY_CENTER, APP_LOCK, SECURITY_CENTER, APP_LOCK))
    }

    @Test
    fun otherActivitiesAreIneligibleEvenIfAnAppLockComponentAppearsInTheTask() {
        assertFalse(isIslandAppLockOpening(OPEN, SECURITY_CENTER, APP_LOCK, SECURITY_CENTER, SECURITY_CENTER_HOME))
        assertFalse(isIslandAppLockOpening(OPEN, SECURITY_CENTER, APP_LOCK, "com.example.app", APP_LOCK))
        assertFalse(isIslandAppLockOpening(OPEN, "com.example.app", APP_LOCK, SECURITY_CENTER, APP_LOCK))
        assertFalse(isIslandAppLockOpening(OPEN, SECURITY_CENTER, SECURITY_CENTER_HOME, SECURITY_CENTER, APP_LOCK))
        assertFalse(isIslandAppLockOpening(OPEN, SECURITY_CENTER, "${APP_LOCK}Lookalike", null, null))
    }

    private companion object {
        const val OPEN = 1
        const val CLOSE = 2
        const val TO_FRONT = 3
        const val TO_BACK = 4
        const val SECURITY_CENTER = "com.miui.securitycenter"
        const val APP_LOCK = "com.miui.applicationlock.AppLockActivity"
        const val CONFIRM_ACCESS_CONTROL = "com.miui.applicationlock.ConfirmAccessControl"
        const val SECURITY_CENTER_HOME = "com.miui.securitycenter.MainActivity"
    }
}
