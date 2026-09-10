/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.preference

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import org.amnezia.awg.Application
import org.amnezia.awg.BuildConfig
import org.amnezia.awg.R
import org.amnezia.awg.backend.Backend
import org.amnezia.awg.backend.GoBackend
import org.amnezia.awg.backend.AwgQuickBackend
import org.amnezia.awg.util.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VersionPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    private var versionSummary: String? = null

    override fun getSummary() = versionSummary

    override fun getTitle() = context.getString(R.string.version_title, BuildConfig.VERSION_NAME)

        companion object {
        private fun getBackendPrettyName(context: Context, backend: Backend) = when (backend) {
            is AwgQuickBackend -> context.getString(R.string.type_name_kernel_module)
            is GoBackend -> context.getString(R.string.type_name_go_userspace)
            else -> ""
        }
    }

    init {
        lifecycleScope.launch {
            val backend = Application.getBackend()
            versionSummary = getContext().getString(R.string.version_summary_checking, getBackendPrettyName(context, backend).lowercase())
            notifyChanged()
            versionSummary = try {
                getContext().getString(R.string.version_summary, getBackendPrettyName(context, backend), withContext(Dispatchers.IO) { backend.version })
            } catch (_: Throwable) {
                getContext().getString(R.string.version_summary_unknown, getBackendPrettyName(context, backend).lowercase())
            }
            notifyChanged()
        }
    }
}
