/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.inputmethod.latin.settings;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Process;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;

import com.android.inputmethod.latin.Dictionary;
import com.android.inputmethod.latin.DictionaryDumpBroadcastReceiver;
import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.debug.ExternalDictionaryGetterForDebug;
import com.android.inputmethod.latin.utils.ApplicationUtils;
import com.android.inputmethod.latin.utils.ResourceUtils;

public final class DebugSettings extends PreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String PREF_DEBUG_MODE = "debug_mode";
    public static final String PREF_FORCE_NON_DISTINCT_MULTITOUCH = "force_non_distinct_multitouch";
    public static final String PREF_USABILITY_STUDY_MODE = "usability_study_mode";
    public static final String PREF_STATISTICS_LOGGING = "enable_logging";
    public static final String PREF_USE_ONLY_PERSONALIZATION_DICTIONARY_FOR_DEBUG =
            "use_only_personalization_dictionary_for_debug";
    public static final String PREF_KEY_PREVIEW_SHOW_UP_START_SCALE =
            "pref_key_preview_show_up_start_scale";
    public static final String PREF_KEY_PREVIEW_DISMISS_END_SCALE =
            "pref_key_preview_dismiss_end_scale";
    public static final String PREF_KEY_PREVIEW_SHOW_UP_DURATION =
            "pref_key_preview_show_up_duration";
    public static final String PREF_KEY_PREVIEW_DISMISS_DURATION =
            "pref_key_preview_dismiss_duration";
    private static final String PREF_READ_EXTERNAL_DICTIONARY = "read_external_dictionary";
    private static final String PREF_DUMP_CONTACTS_DICT = "dump_contacts_dict";
    private static final String PREF_DUMP_USER_DICT = "dump_user_dict";
    private static final String PREF_DUMP_USER_HISTORY_DICT = "dump_user_history_dict";
    private static final String PREF_DUMP_PERSONALIZATION_DICT = "dump_personalization_dict";

    private static final boolean SHOW_STATISTICS_LOGGING = false;

    private boolean mServiceNeedsRestart = false;
    private CheckBoxPreference mDebugMode;
    private CheckBoxPreference mStatisticsLoggingPref;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs_for_debug);
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        prefs.registerOnSharedPreferenceChangeListener(this);

        final Preference usabilityStudyPref = findPreference(PREF_USABILITY_STUDY_MODE);
        if (usabilityStudyPref instanceof CheckBoxPreference) {
            final CheckBoxPreference checkbox = (CheckBoxPreference)usabilityStudyPref;
            checkbox.setChecked(prefs.getBoolean(PREF_USABILITY_STUDY_MODE,
                    LatinImeLogger.getUsabilityStudyMode(prefs)));
            checkbox.setSummary(R.string.settings_warning_researcher_mode);
        }
        final Preference statisticsLoggingPref = findPreference(PREF_STATISTICS_LOGGING);
        if (statisticsLoggingPref instanceof CheckBoxPreference) {
            mStatisticsLoggingPref = (CheckBoxPreference) statisticsLoggingPref;
            if (!SHOW_STATISTICS_LOGGING) {
                getPreferenceScreen().removePreference(statisticsLoggingPref);
            }
        }

        final PreferenceScreen readExternalDictionary =
                (PreferenceScreen) findPreference(PREF_READ_EXTERNAL_DICTIONARY);
        if (null != readExternalDictionary) {
            readExternalDictionary.setOnPreferenceClickListener(
                    new Preference.OnPreferenceClickListener() {
                        @Override
                        public boolean onPreferenceClick(final Preference arg0) {
                            ExternalDictionaryGetterForDebug.chooseAndInstallDictionary(
                                    getActivity());
                            mServiceNeedsRestart = true;
                            return true;
                        }
                    });
        }

        final OnPreferenceClickListener dictDumpPrefClickListener =
                new DictDumpPrefClickListener(this);
        findPreference(PREF_DUMP_CONTACTS_DICT).setOnPreferenceClickListener(
                dictDumpPrefClickListener);
        findPreference(PREF_DUMP_USER_DICT).setOnPreferenceClickListener(
                dictDumpPrefClickListener);
        findPreference(PREF_DUMP_USER_HISTORY_DICT).setOnPreferenceClickListener(
                dictDumpPrefClickListener);
        findPreference(PREF_DUMP_PERSONALIZATION_DICT).setOnPreferenceClickListener(
                dictDumpPrefClickListener);
        final Resources res = getResources();
        setupKeyPreviewAnimationDuration(prefs, res, PREF_KEY_PREVIEW_SHOW_UP_DURATION,
                res.getInteger(R.integer.config_key_preview_show_up_duration));
        setupKeyPreviewAnimationDuration(prefs, res, PREF_KEY_PREVIEW_DISMISS_DURATION,
                res.getInteger(R.integer.config_key_preview_dismiss_duration));
        setupKeyPreviewAnimationScale(prefs, res, PREF_KEY_PREVIEW_SHOW_UP_START_SCALE,
                ResourceUtils.getFloatFromFraction(
                        res, R.fraction.config_key_preview_show_up_start_scale));
        setupKeyPreviewAnimationScale(prefs, res, PREF_KEY_PREVIEW_DISMISS_END_SCALE,
                ResourceUtils.getFloatFromFraction(
                        res, R.fraction.config_key_preview_dismiss_end_scale));

        mServiceNeedsRestart = false;
        mDebugMode = (CheckBoxPreference) findPreference(PREF_DEBUG_MODE);
        updateDebugMode();
    }

    private static class DictDumpPrefClickListener implements OnPreferenceClickListener {
        final PreferenceFragment mPreferenceFragment;

        public DictDumpPrefClickListener(final PreferenceFragment preferenceFragment) {
            mPreferenceFragment = preferenceFragment;
        }

        @Override
        public boolean onPreferenceClick(final Preference arg0) {
            final String dictName;
            if (arg0.getKey().equals(PREF_DUMP_CONTACTS_DICT)) {
                dictName = Dictionary.TYPE_CONTACTS;
            } else if (arg0.getKey().equals(PREF_DUMP_USER_DICT)) {
                dictName = Dictionary.TYPE_USER;
            } else if (arg0.getKey().equals(PREF_DUMP_USER_HISTORY_DICT)) {
                dictName = Dictionary.TYPE_USER_HISTORY;
            } else if (arg0.getKey().equals(PREF_DUMP_PERSONALIZATION_DICT)) {
                dictName = Dictionary.TYPE_PERSONALIZATION;
            } else {
                dictName = null;
            }
            if (dictName != null) {
                final Intent intent =
                        new Intent(DictionaryDumpBroadcastReceiver.DICTIONARY_DUMP_INTENT_ACTION);
                intent.putExtra(DictionaryDumpBroadcastReceiver.DICTIONARY_NAME_KEY, dictName);
                mPreferenceFragment.getActivity().sendBroadcast(intent);
            }
            return true;
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mServiceNeedsRestart) Process.killProcess(Process.myPid());
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (key.equals(PREF_DEBUG_MODE)) {
            if (mDebugMode != null) {
                mDebugMode.setChecked(prefs.getBoolean(PREF_DEBUG_MODE, false));
                final boolean checked = mDebugMode.isChecked();
                if (mStatisticsLoggingPref != null) {
                    if (checked) {
                        getPreferenceScreen().addPreference(mStatisticsLoggingPref);
                    } else {
                        getPreferenceScreen().removePreference(mStatisticsLoggingPref);
                    }
                }
                updateDebugMode();
                mServiceNeedsRestart = true;
            }
        } else if (key.equals(PREF_FORCE_NON_DISTINCT_MULTITOUCH)) {
            mServiceNeedsRestart = true;
        }
    }

    private void updateDebugMode() {
        if (mDebugMode == null) {
            return;
        }
        boolean isDebugMode = mDebugMode.isChecked();
        final String version = getResources().getString(
                R.string.version_text, ApplicationUtils.getVersionName(getActivity()));
        if (!isDebugMode) {
            mDebugMode.setTitle(version);
            mDebugMode.setSummary("");
        } else {
            mDebugMode.setTitle(getResources().getString(R.string.prefs_debug_mode));
            mDebugMode.setSummary(version);
        }
    }

    private void setupKeyPreviewAnimationScale(final SharedPreferences sp, final Resources res,
            final String prefKey, final float defaultValue) {
        final SeekBarDialogPreference pref = (SeekBarDialogPreference)findPreference(prefKey);
        if (pref == null) {
            return;
        }
        pref.setInterface(new SeekBarDialogPreference.ValueProxy() {
            private static final float PERCENTAGE_FLOAT = 100.0f;

            private float getValueFromPercentage(final int percentage) {
                return percentage / PERCENTAGE_FLOAT;
            }

            private int getPercentageFromValue(final float floatValue) {
                return (int)(floatValue * PERCENTAGE_FLOAT);
            }

            @Override
            public void writeValue(final int value, final String key) {
                sp.edit().putFloat(key, getValueFromPercentage(value)).apply();
            }

            @Override
            public void writeDefaultValue(final String key) {
                sp.edit().remove(key).apply();
            }

            @Override
            public int readValue(final String key) {
                return getPercentageFromValue(
                        Settings.readKeyPreviewAnimationScale(sp, key, defaultValue));
            }

            @Override
            public int readDefaultValue(final String key) {
                return getPercentageFromValue(defaultValue);
            }

            @Override
            public String getValueText(final int value) {
                if (value < 0) {
                    return res.getString(R.string.settings_system_default);
                }
                return String.format("%d%%", value);
            }

            @Override
            public void feedbackValue(final int value) {}
        });
    }

    private void setupKeyPreviewAnimationDuration(final SharedPreferences sp, final Resources res,
            final String prefKey, final int defaultValue) {
        final SeekBarDialogPreference pref = (SeekBarDialogPreference)findPreference(prefKey);
        if (pref == null) {
            return;
        }
        pref.setInterface(new SeekBarDialogPreference.ValueProxy() {
            @Override
            public void writeValue(final int value, final String key) {
                sp.edit().putInt(key, value).apply();
            }

            @Override
            public void writeDefaultValue(final String key) {
                sp.edit().remove(key).apply();
            }

            @Override
            public int readValue(final String key) {
                return Settings.readKeyPreviewAnimationDuration(sp, key, defaultValue);
            }

            @Override
            public int readDefaultValue(final String key) {
                return defaultValue;
            }

            @Override
            public String getValueText(final int value) {
                return res.getString(R.string.abbreviation_unit_milliseconds, value);
            }

            @Override
            public void feedbackValue(final int value) {}
        });
    }
}
