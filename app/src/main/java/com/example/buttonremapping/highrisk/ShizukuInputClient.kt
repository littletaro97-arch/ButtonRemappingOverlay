package com.example.buttonremapping.highrisk

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import com.example.buttonremapping.RuntimeProtection
import rikka.shizuku.Shizuku

class ShizukuInputClient(
    private val context: Context,
    private val onConnected: () -> Unit,
    private val onDisconnected: (String) -> Unit,
) {
    private val args = Shizuku.UserServiceArgs(
        ComponentName(context, InputUserService::class.java),
    )
        .daemon(false)
        .tag("single-button-input-v04")
        .version(1)
        .processNameSuffix("input")

    @Volatile
    private var remote: IInputUserService? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            remote = service?.let(IInputUserService.Stub::asInterface)
            if (remote == null) {
                RuntimeProtection.recordEvent(context, "Shizuku UserService 连接失败：Binder 为空")
                onDisconnected("UserService Binder 为空")
            } else {
                RuntimeProtection.recordEvent(context, "Shizuku UserService 已连接")
                onConnected()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            remote = null
            RuntimeProtection.recordEvent(context, "Shizuku UserService 已断开")
            onDisconnected("UserService 已断开")
        }
    }

    fun isBound(): Boolean = bound

    /** remote 是否已连接（可注入）。 */
    fun isRemoteReady(): Boolean = remote != null

    fun bind(): Boolean {
        return try {
            if (HighRiskManager.getShizukuStatus(context).state != ShizukuState.READY) {
                RuntimeProtection.recordEvent(context, "Shizuku UserService 绑定失败：状态未就绪")
                false
            } else {
                Shizuku.bindUserService(args, connection)
                bound = true
                RuntimeProtection.recordEvent(context, "请求绑定 Shizuku UserService")
                true
            }
        } catch (error: Throwable) {
            RuntimeProtection.recordEvent(context, "Shizuku UserService 启动失败", error.javaClass.simpleName)
            onDisconnected("UserService 启动失败：${error.javaClass.simpleName}")
            false
        }
    }

    fun injectTap(x: Int, y: Int, displayId: Int): Boolean = try {
        remote?.injectTap(x, y, displayId) == true
    } catch (error: Throwable) {
        RuntimeProtection.recordEvent(context, "调用 Shizuku UserService 注入失败", error.javaClass.simpleName)
        false
    }

    fun close() {
        remote = null
        if (!bound) return
        bound = false
        try {
            Shizuku.unbindUserService(args, connection, true)
        } catch (_: Throwable) {
            // Shizuku may already be stopped; the service is still removed locally.
        }
        RuntimeProtection.recordEvent(context, "解除绑定 Shizuku UserService")
    }
}
