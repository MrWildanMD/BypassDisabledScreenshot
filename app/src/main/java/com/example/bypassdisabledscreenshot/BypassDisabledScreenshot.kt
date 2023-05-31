package com.example.bypassdisabledscreenshot

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.util.function.BiPredicate

class BypassDisabledScreenshot : IXposedHookLoadPackage {
    companion object {
        lateinit var deoptimizeMethod : Method
        init {
            var m: Method? = null
            try {
                m = XposedBridge::class.java.getDeclaredMethod("deoptimizeMethod", Member::class.java)
            } catch (t : Throwable) {
                XposedBridge.log(t)
            }
            if (m != null) {
                deoptimizeMethod = m
            }
        }
        @Throws(InvocationTargetException::class, IllegalAccessException::class)
        fun deoptimizeMethod(c: Class<*>, n: String) {
            for (m in c.declaredMethods) {
                if (deoptimizeMethod != null && m.name == n) {
                    deoptimizeMethod.invoke(null, m)
                    Log.d("DisableScreenshot", "Deoptimized $m")
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam?) {
        XposedBridge.log("Loaded app: " + lpparam!!.packageName)
        if (lpparam.packageName.equals("android")) {
            try {
                var windowState : Class<*> = XposedHelpers.findClass("com.android.server.wm.WindowState", lpparam!!.classLoader)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    XposedHelpers.findAndHookMethod(
                        windowState,
                        "isSecureLocked",
                        XC_MethodReplacement.returnConstant(false)
                    )
                } else {
                    XposedHelpers.findAndHookMethod(
                        "com.android.server.wm.WindowManagerService",
                        lpparam.classLoader,
                        "isSecureLocked",
                        windowState,
                        XC_MethodReplacement.returnConstant(false)
                    )
                }
            } catch (t : Throwable) {
                XposedBridge.log(t)
            }
            try {
                deoptimizeMethod(XposedHelpers.findClass("com.android.server.wm.WindowStateAnimator", lpparam.classLoader))
                var c = XposedHelpers.findClass("com.android.server.display.DisplayManagerService", lpparam.classLoader)
                deoptimizeMethod(c, "setUserPreferredModeForDisplayLocked")
                deoptimizeMethod(c, "setUserPreferredDisplayModeInternal")
                c = XposedHelpers.findClass("com.android.server.wm.InsetsPolicy\$InsetsPolicyAnimationControlListener", lpparam.classLoader)
                for (m in c.declaredConstructors) {
                    deoptimizeMethod.invoke(null, m)
                }
                c = XposedHelpers.findClass("com.android.server.wm.InsetsPolicy", lpparam.classLoader)
                deoptimizeMethod(c, "startAnimation")
                deoptimizeMethod(c, "controlAnimationUnchecked")
                for (i in 0..19) {
                    c = XposedHelpers.findClassIfExists(
                        "com.android.server.wm.DisplayContent$\$ExternalSyntheticLambda$i",
                        lpparam.classLoader
                    )
                    if (c != null && BiPredicate::class.java.isAssignableFrom(c)) {
                        deoptimizeMethod(c, "test")
                    }
                }
            } catch (t : Throwable) {
                XposedBridge.log(t)
            }
        }
    }
}