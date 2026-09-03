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
            // TODO: 更新清理策略
            true
        }
    }
}
