package com.lidesheng.hyperlyric.root.statusbar

import android.view.View
import android.view.ViewGroup
import com.lidesheng.hyperlyric.root.managedHook
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule

/** Installs the Xiaomi HyperOS status-bar lyric host hook. */
internal object StatusBarLyricHooker {
    private const val TAG = "StatusBarLyricHooker"
    private const val STATUS_BAR_VIEW_CLASS =
        "com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView"
    private const val MIUI_CLOCK_CLASS = "com.android.systemui.statusbar.views.MiuiClock"
    private const val MIUI_COLLAPSED_STATUS_BAR_FRAGMENT_CLASS =
        "com.android.systemui.statusbar.phone.MiuiCollapsedStatusBarFragment"

    fun hook(module: XposedModule, classLoader: ClassLoader) {
        runCatching {
            StatusBarLyricIslandRegionHooker.hook(module, classLoader)
        }.onFailure { error ->
            HookLogger.w(
                TAG,
                "原生超级岛区域监听未安装: reason=${error.message}"
            )
        }
        hookClockVisibility(module, classLoader)
        hookNotificationIconContainer(module, classLoader)
        hookNotificationIconVisibility(module, classLoader)

        val method = runCatching {
            classLoader.loadClass(STATUS_BAR_VIEW_CLASS)
                .declaredMethods
                .firstOrNull { candidate ->
                    candidate.name == "onFinishInflate" &&
                            candidate.parameterCount == 0 &&
                            candidate.returnType == Void.TYPE
                }
        }.getOrNull()

        if (method == null) {
            HookLogger.w(TAG, "状态栏歌词 Hook 未安装: reason=target_method_unavailable")
            return
        }

        runCatching {
            method.isAccessible = true
            module.managedHook(
                executable = method,
                capability = "status_bar.lyric.clock_host",
                hooker = StatusBarInflatedHook(),
            )
        }.onFailure { error ->
            HookLogger.w(
                TAG,
                "状态栏歌词 Hook 未安装: reason=${error.message}"
            )
        }
    }

    private fun hookNotificationIconContainer(module: XposedModule, classLoader: ClassLoader) {
        val method = runCatching {
            classLoader.loadClass(STATUS_BAR_VIEW_CLASS).declaredMethods.firstOrNull { candidate ->
                candidate.name == "setNotificationIconAreaInnner" &&
                        candidate.parameterTypes.contentEquals(arrayOf(View::class.java)) &&
                        candidate.returnType == Void.TYPE
            }
        }.getOrNull()
        if (method == null) {
            HookLogger.w(
                TAG,
                "状态栏歌词通知图标容器未接入: reason=container_method_unavailable"
            )
            return
        }
        runCatching {
            method.isAccessible = true
            module.managedHook(
                executable = method,
                capability = "status_bar.lyric.notification_icon_container",
                hooker = NotificationIconContainerHook(),
            )
        }.onFailure { error ->
            HookLogger.w(
                TAG,
                "状态栏歌词通知图标容器未接入: reason=${error.message}"
            )
        }
    }

    private fun hookNotificationIconVisibility(module: XposedModule, classLoader: ClassLoader) {
        val methods = runCatching {
            classLoader.loadClass(MIUI_COLLAPSED_STATUS_BAR_FRAGMENT_CLASS)
                .declaredMethods.filter { method ->
                    val parameters = method.parameterTypes
                    method.returnType == Void.TYPE && when (method.name) {
                        "animateShow" -> parameters.size == 3 &&
                                parameters[0] == View::class.java &&
                                parameters[1] == Boolean::class.javaPrimitiveType &&
                                parameters[2] == Boolean::class.javaPrimitiveType

                        "animateHiddenState" -> parameters.size == 4 &&
                                parameters[0] == Int::class.javaPrimitiveType &&
                                parameters[1] == View::class.java &&
                                parameters[2] == Boolean::class.javaPrimitiveType &&
                                parameters[3] == Boolean::class.javaPrimitiveType

                        else -> false
                    }
                }
        }.getOrNull().orEmpty()

        if (methods.isEmpty()) {
            HookLogger.w(
                TAG,
                "状态栏歌词通知图标显隐保护未安装: reason=visibility_methods_unavailable"
            )
            return
        }
        methods.forEach { method ->
            runCatching {
                method.isAccessible = true
                val isShowMethod = method.name == "animateShow"
                module.managedHook(
                    executable = method,
                    capability = "status_bar.lyric.notification_icon_visibility",
                    hooker = NotificationIconVisibilityHook(
                        viewArgumentIndex = if (isShowMethod) 0 else 1,
                        visibilityArgumentIndex = if (isShowMethod) null else 0,
                    ),
                )
            }.onFailure { error ->
                HookLogger.w(
                    TAG,
                    "状态栏歌词通知图标显隐保护未安装: method=${method.name}, reason=${error.message}"
                )
            }
        }
    }

    private fun hookClockVisibility(module: XposedModule, classLoader: ClassLoader) {
        val methods = runCatching {
            classLoader.loadClass(MIUI_CLOCK_CLASS).declaredMethods.filter { method ->
                method.name == "updateClockVisibility" &&
                        method.parameterCount == 0 && method.returnType == Void.TYPE
            }
        }.getOrNull().orEmpty()

        if (methods.isEmpty()) {
            HookLogger.w(TAG, "状态栏歌词时钟保护未安装: reason=visibility_method_unavailable")
            return
        }

        methods.forEach { method ->
            runCatching {
                method.isAccessible = true
                module.managedHook(
                    executable = method,
                    capability = "status_bar.lyric.clock_visibility_guard",
                    hooker = ClockVisibilityHook(),
                )
            }.onFailure { error ->
                HookLogger.w(TAG, "状态栏歌词时钟保护未安装: reason=${error.message}")
            }
        }
    }

    private class StatusBarInflatedHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            val root = chain.thisObject as? ViewGroup ?: return result
            StatusBarLyricHostRegistry.registerInflatedRoot(root)
            return result
        }
    }

    private class NotificationIconContainerHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            val statusBar = chain.thisObject as? ViewGroup ?: return result
            val iconContainer = chain.args.firstOrNull() as? View ?: return result
            StatusBarLyricHostRegistry.registerNotificationIconContainer(statusBar, iconContainer)
            return result
        }
    }

    private class NotificationIconVisibilityHook(
        private val viewArgumentIndex: Int,
        private val visibilityArgumentIndex: Int?,
    ) : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            val view = chain.args.getOrNull(viewArgumentIndex) as? View ?: return result
            val visibility = visibilityArgumentIndex?.let { index ->
                (chain.args.getOrNull(index) as? Number)?.toInt()
            } ?: View.VISIBLE
            StatusBarLyricHostRegistry.onNotificationIconNativeVisibilityRequested(
                view = view,
                visibility = visibility,
            )
            return result
        }
    }

    private class ClockVisibilityHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            val clock = chain.thisObject as? android.view.View ?: return result
            StatusBarLyricRenderer.onClockVisibilityUpdated(
                clock = clock,
                systemVisibility = systemRequestedVisibility(clock),
            )
            return result
        }

        private fun systemRequestedVisibility(clock: android.view.View): Int? = runCatching {
            val policyVisibility = clock.javaClass.getField("mPolicyVisibility").getInt(clock)
            val multiTaskVisibility = clock.javaClass.getField("mMultiTaskVisibility").getInt(clock)
            when {
                policyVisibility == android.view.View.GONE ||
                        multiTaskVisibility == android.view.View.GONE -> android.view.View.GONE

                policyVisibility == android.view.View.INVISIBLE ||
                        multiTaskVisibility == android.view.View.INVISIBLE -> android.view.View.INVISIBLE

                else -> android.view.View.VISIBLE
            }
        }.getOrNull()
    }
}
