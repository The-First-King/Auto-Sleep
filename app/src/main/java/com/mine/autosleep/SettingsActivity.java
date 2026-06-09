package com.mine.autosleep;

import android.app.ActionBar;
import android.app.Activity;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.v4.app.NavUtils;
import android.view.MenuItem;
import android.widget.Toast;

public class SettingsActivity extends Activity
{
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class SettingsFragment extends PreferenceFragment
    {
        @Override
        public void onCreate(final Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);

            // Validate Popup Countdown (1 to 300 seconds)
            EditTextPreference popupPref = (EditTextPreference) findPreference(Constants.PREF_POPUP_COUNTDOWN);
            if (popupPref != null) {
                popupPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        try {
                            int val = Integer.parseInt(newValue.toString());
                            if (val >= 1 && val <= 300) {
                                return true;
                            }
                        } catch (NumberFormatException ignored) {}
                        
                        Toast.makeText(getActivity(), "Please enter a value between 1 and 300 seconds", Toast.LENGTH_SHORT).show();
                        return false;
                    }
                });
            }

            // Validate Delay Timer (0 to 30 minutes)
            EditTextPreference delayPref = (EditTextPreference) findPreference(Constants.PREF_DELAY_TIMER);
            if (delayPref != null) {
                delayPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        try {
                            int val = Integer.parseInt(newValue.toString());
                            if (val >= 0 && val <= 30) {
                                return true;
                            }
                        } catch (NumberFormatException ignored) {}
                        
                        Toast.makeText(getActivity(), "Please enter a value between 0 and 30 minutes", Toast.LENGTH_SHORT).show();
                        return false;
                    }
                });
            }
        }
    }
}
