package com.minimal.launcher

import android.content.ComponentName
import android.os.UserHandle
import java.util.Locale

data class AppEntry(
    val packageName: String,
    val activityName: String,
    val componentName: ComponentName,
    val userHandle: UserHandle,
    val userSerial: Long,
    val originalLabel: String,
    val customLabel: String?,
    val isWorkProfile: Boolean
) {
    val idKey: String
        get() = "$packageName/$activityName/$userSerial"

    val displayLabel: String
        get() = (customLabel ?: originalLabel).lowercase(Locale.getDefault())
}
