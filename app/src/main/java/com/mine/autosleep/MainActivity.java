package com.mine.autosleep;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private final AlarmBroadcastReceiver sleepBroadcastReceiver = new AlarmBroadcastReceiver();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (RootUtil.isDeviceRooted()) {
            try {
                Process p = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(p.getOutputStream());
                os.writeBytes("pm grant " + getApplicationContext().getPackageName() + " android.permission.WRITE_SECURE_SETTINGS \n");
                os.writeBytes("exit\n");
                os.flush();
            } catch (RuntimeException | IOException e) {
                Log.e(TAG, "Exception: " + e.getMessage());
            }
        } else {
            new AlertDialog.Builder(MainActivity.this)
                .setTitle(R.string.root_dialog_title)
                .setMessage(getString(R.string.root_dialog_desc_main) + "\n\n" + getString(R.string.root_dialog_desc_exit))
                .setCancelable(false)
                .setPositiveButton(R.string.root_dialog_ok_button, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        moveTaskToBack(true);
                        android.os.Process.killProcess(android.os.Process.myPid());
                        System.exit(1);
                    }
                }).show();
        }

        final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        final Switch switchApp = (Switch) findViewById(R.id.switchApp);
        final TextView disableSleepText = (TextView) findViewById(R.id.disableSleepText);
        final TextView editDisableSleep = (TextView) findViewById(R.id.editDisableSleep);
        final TextView enableSleepText = (TextView) findViewById(R.id.enableSleepText);
        final TextView editEnableSleep = (TextView) findViewById(R.id.editEnableSleep);

        final RadioGroup radioGroupStartDay = (RadioGroup) findViewById(R.id.radioGroupStartDay);
        final RadioButton radioToday = (RadioButton) findViewById(R.id.radioToday);
        final RadioButton radioTomorrow = (RadioButton) findViewById(R.id.radioTomorrow);

        boolean startNextDay = settings.getBoolean(Constants.START_ON_NEXT_DAY, false);
        if (startNextDay) {
            radioTomorrow.setChecked(true);
        } else {
            radioToday.setChecked(true);
        }

        switchApp.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                sleepBroadcastReceiver.cancelAlarms(MainActivity.this);
                if (isChecked) {
                    String message = sleepBroadcastReceiver.setAlarms(MainActivity.this);
                    if (!message.isEmpty()) {
                        displayToast(message);
                    }
                }

                // Removed 'nextDay' from this list to prevent crash
                for (View v : Arrays.asList(disableSleepText, editDisableSleep, enableSleepText, editEnableSleep, radioGroupStartDay)) {
                    v.setEnabled(isChecked);
                }
                
                radioToday.setEnabled(isChecked);
                radioTomorrow.setEnabled(isChecked);

                SharedPreferences.Editor editor = settings.edit();
                editor.putBoolean(Constants.APP_IS_ENABLED, isChecked);
                editor.apply();
            }
        });

        radioGroupStartDay.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                SharedPreferences.Editor editor = settings.edit();
                editor.putBoolean(Constants.START_ON_NEXT_DAY, checkedId == R.id.radioTomorrow);
                editor.apply();

                if (switchApp.isChecked()) {
                    sleepBroadcastReceiver.cancelAlarms(MainActivity.this);
                    String message = sleepBroadcastReceiver.setAlarms(MainActivity.this);
                    if (!message.isEmpty()) displayToast(message);
                }
            }
        });

        editEnableSleep.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new TimePickerDialog(MainActivity.this, new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                        editEnableSleep.setText(String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute));
                        saveClocks();
                        if (switchApp.isChecked()) {
                            sleepBroadcastReceiver.cancelAlarms(MainActivity.this);
                            String message = sleepBroadcastReceiver.setAlarms(MainActivity.this);
                            if (!message.isEmpty()) displayToast(message);
                        }
                    }
                }, 23, 0, true).show();
            }
        });

        editDisableSleep.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new TimePickerDialog(MainActivity.this, new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                        editDisableSleep.setText(String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute));
                        saveClocks();
                        if (switchApp.isChecked()) {
                            sleepBroadcastReceiver.cancelAlarms(MainActivity.this);
                            String message = sleepBroadcastReceiver.setAlarms(MainActivity.this);
                            if (!message.isEmpty()) displayToast(message);
                        }
                    }
                }, 8, 0, true).show();
            }
        });

        editEnableSleep.setText(settings.getString(Constants.ENABLE_SLEEP_TIME, "23:00"));
        editDisableSleep.setText(settings.getString(Constants.DISABLE_SLEEP_TIME, "08:00"));

        switchApp.setChecked(settings.getBoolean(Constants.APP_IS_ENABLED, false));
    }

    private void displayToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean onCreateOptionsMenu (Menu menu) {
        getMenuInflater().inflate(R.menu.settings, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_settings) {
            Intent settingsIntent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(settingsIntent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStop() {
        super.onStop();
        saveClocks();
    }

    private void saveClocks() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = settings.edit();
        final TextView editEnableSleep = (TextView) findViewById(R.id.editEnableSleep);
        final TextView editDisableSleep = (TextView) findViewById(R.id.editDisableSleep);
        editor.putString(Constants.ENABLE_SLEEP_TIME, editEnableSleep.getText().toString());
        editor.putString(Constants.DISABLE_SLEEP_TIME, editDisableSleep.getText().toString());
        editor.apply();
    }
}
