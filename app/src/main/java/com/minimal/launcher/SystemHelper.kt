package com.minimal.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Process
import android.os.UserManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.widget.Toast

object SystemHelper {

    fun queryAllApps(context: Context, prefs: LauncherPrefs): List<AppEntry> {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        val selfPackage = context.packageName
        val primaryUser = Process.myUserHandle()
        val profiles = userManager.userProfiles.ifEmpty { listOf(primaryUser) }

        val entries = mutableListOf<AppEntry>()
        for (user in profiles) {
            val userSerial = userManager.getSerialNumberForUser(user)
            val isWork = user != primaryUser
            val activities = try {
                launcherApps.getActivityList(null, user)
            } catch (_: Exception) {
                emptyList()
            }
            for (info in activities) {
                val pkg = info.applicationInfo.packageName
                if (pkg == selfPackage) continue
                val component = info.componentName
                val activityName = component.className
                val key = "$pkg/$activityName/$userSerial"
                entries.add(
                    AppEntry(
                        packageName = pkg,
                        activityName = activityName,
                        componentName = component,
                        userHandle = user,
                        userSerial = userSerial,
                        originalLabel = info.label?.toString() ?: pkg,
                        customLabel = prefs.getCustomLabel(key),
                        isWorkProfile = isWork
                    )
                )
            }
        }
        return entries.sortedBy { it.displayLabel }
    }

    fun launchApp(context: Context, entry: AppEntry) {
        try {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            launcherApps.startMainActivity(entry.componentName, entry.userHandle, null, null)
        } catch (_: Exception) {
            Toast.makeText(context, "unable to open ${entry.displayLabel}", Toast.LENGTH_SHORT).show()
        }
    }

    fun openAppInfo(context: Context, entry: AppEntry) {
        try {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            launcherApps.startAppDetailsActivity(entry.componentName, entry.userHandle, null, null)
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${entry.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(context, "unable to open app info", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun uninstallApp(context: Context, entry: AppEntry) {
        try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:${entry.packageName}")
                putExtra(Intent.EXTRA_USER, entry.userHandle)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "cannot uninstall ${entry.displayLabel}", Toast.LENGTH_SHORT).show()
        }
    }

    fun openClock(context: Context) {
        val candidates = listOf(
            Intent(AlarmClock.ACTION_SHOW_ALARMS),
            Intent(AlarmClock.ACTION_SET_ALARM)
        )
        for (intent in candidates) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) {
                // Try next intent
            }
        }
    }

    fun openCalendar(context: Context) {
        val candidates = listOf(
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR),
            Intent(Intent.ACTION_VIEW).apply {
                data = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build()
            }
        )
        for (intent in candidates) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) {
                // Try next intent
            }
        }
    }

    @SuppressLint("WrongConstant", "PrivateApi")
    fun expandNotifications(context: Context) {
        try {
            val statusBarService = context.getSystemService("statusbar") ?: return
            val statusBarManager = Class.forName("android.app.StatusBarManager")
            val expand = statusBarManager.getMethod("expandNotificationsPanel")
            expand.invoke(statusBarService)
        } catch (_: Exception) {
            // Silently ignore if OEM restricts status bar reflection
        }
    }
}
