package com.example.chargemonitor.ui.settings

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import com.example.chargemonitor.R

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        // 可以在这里添加设置变更监听
        findPreference<ListPreference>("retention_days")?.setOnPreferenceChangeListener { _, _ ->
            // 设置由 ListPreference 自动持久化，清理逻辑会在下次采样时读取最新天数
            true
        }
    }
}
