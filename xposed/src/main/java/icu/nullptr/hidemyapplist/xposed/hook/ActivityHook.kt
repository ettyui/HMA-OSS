package icu.nullptr.hidemyapplist.xposed.hook

import android.content.Intent
import android.os.Build
import com.github.kyuubiran.ezxhelper.init.InitFields
import com.github.kyuubiran.ezxhelper.utils.findMethod
import com.github.kyuubiran.ezxhelper.utils.findMethodOrNull
import com.github.kyuubiran.ezxhelper.utils.hookAfter
import com.github.kyuubiran.ezxhelper.utils.hookBefore
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.XposedHelpers.getStaticIntField
import icu.nullptr.hidemyapplist.common.Utils
import icu.nullptr.hidemyapplist.xposed.HMAService
import icu.nullptr.hidemyapplist.xposed.logD
import icu.nullptr.hidemyapplist.xposed.logE
import icu.nullptr.hidemyapplist.xposed.logI

class ActivityHook(private val service: HMAService) : IFrameworkHook {
    companion object {
        private const val TAG = "ActivityHook"
        private val fakeReturnCode by lazy {
            getStaticIntField(
                findClass(
                    "android.app.ActivityManager",
                    InitFields.ezXClassLoader
                ),
                "START_CLASS_NOT_FOUND"
            )
        }
    }

    private val hooks = mutableListOf<XC_MethodHook.Unhook>()

    override fun load() {
        logI(TAG, "Load hook")

        hooks += findMethod(
            "com.android.server.wm.ActivityStarter"
        ) {
            name == "execute"
        }.hookBefore { param ->
            runCatching {
                val request = getObjectField(param.thisObject, "mRequest")
                val caller = getObjectField(request, "callingPackage") as String?
                val intent = getObjectField(request, "intent") as Intent?
                val targetApp = intent?.component?.packageName

                if (service.shouldHideActivityLaunch(caller, targetApp)) {
                    logD(
                        TAG,
                        "@executeRequest: insecure query from $caller, target: ${intent?.component}"
                    )
                    param.result = fakeReturnCode
                    service.filterCount++
                }
            }.onFailure {
                logE(TAG, "Fatal error occurred, ignore hook\n", it)
                // unload()
            }
        }

        findMethodOrNull(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                "com.android.server.wm.ActivityTaskSupervisor"
            } else {
                "com.android.server.wm.ActivityStackSupervisor"
            }
        ) {
            name == "checkStartAnyActivityPermission"
        }?.hookAfter { param ->
            var throwable = param.throwable

            while (throwable != null) {
                val newTrace = throwable.stackTrace.filter { item ->
                    !Utils.containsMultiple(
                        item.className,
                        "HookBridge",
                        "LSPHooker",
                        "LSPosed",
                    )
                }

                if (newTrace.size != throwable.stackTrace.size) {
                    logD(TAG, "@checkStartAnyActivityPermission: ${throwable.stackTrace.size - newTrace.size} remnants cleared!")
                    throwable.stackTrace = newTrace.toTypedArray()
                    service.filterCount++
                }

                throwable = throwable.cause
            }
        }?.let {
            logD(TAG, "Loaded checkStartAnyActivityPermission hook from ${it.hookedMethod.declaringClass}!")
            hooks += it
        }
    }

    override fun unload() {
        hooks.forEach(XC_MethodHook.Unhook::unhook)
        hooks.clear()
    }
}
