package com.minimal.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class LockAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Unused: service exists only for GLOBAL_ACTION_LOCK_SCREEN on double-tap.
    }

    override fun onInterrupt() {
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    companion object {
        @Volatile
        private var instance: LockAccessibilityService? = null

        fun lockScreen(context: Context) {
            val service = instance
            if (service == null) {
                Toast.makeText(
                    context,
                    "enable accessibility to use double-tap lock",
                    Toast.LENGTH_SHORT
                ).show()
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                } catch (_: Throwable) {
                }
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                } catch (_: Throwable) {
                }
            } else {
                Toast.makeText(context, "lock screen requires android 9+", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
