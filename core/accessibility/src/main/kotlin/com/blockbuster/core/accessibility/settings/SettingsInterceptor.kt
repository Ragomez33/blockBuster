package com.blockbuster.core.accessibility.settings

import com.blockbuster.core.common.model.SensitiveSystemScreen

interface SettingsInterceptor {
    fun inspect(packageName: String?, className: String?): SensitiveSystemScreen?
}
