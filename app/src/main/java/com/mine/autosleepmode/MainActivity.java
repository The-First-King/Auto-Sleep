package com.mine.autosleepmode;

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
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    private final AlarmBroadcastReceiver sleepBroadcastReceiver = new AlarmBroadcastReceiver();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Root logic with automatic permission grant
        if (RootUtil.isDeviceRooted()) {
            try {
                Process p = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(p.getOutputStream());
                os.writeBytes("pm grant " + getApplicationContext().getPackageName() + " android.permission.WRITE_SECURE_SETTINGS \n");
                os.writeBytes("exit\n");
                os.flush();
            } catch (RuntimeException | IOException e) {
                Log.e(TAG, "Exception during root permission grant: " + e.getMessage());
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

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        // Setup Enable Sleep Switch
        Switch switchEnableSleep = findViewById(R.id.switchEnableSleep);
        switchEnableSleep.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                sleepBroadcastReceiver.cancelAlarm(MainActivity.this, Constants.ID_ENABLE);
                TextView editEnableSleep = findViewById(R.id.editEnableSleep);
                editEnableSleep.setEnabled(isChecked);

                if (isChecked) {
                    sleepBroadcastReceiver.setAlarmEnableSleepMode(MainActivity.this);
                } else {
                    displayToast("Auto Sleep Mode activation disabled");
                }

                SharedPreferences s = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                SharedPreferences.Editor editor = s.edit();
                editor.putBoolean(Constants.AUTOMATIC_ENABLE, isChecked);
                editor.apply();
                updateNextDay();
            }
        });

        // Setup Disable Sleep Switch
        Switch switchDisableSleep = findViewById(R.id.switchDisableSleep);
        switchDisableSleep.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
             @Override
             public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                 sleepBroadcastReceiver.cancelAlarm(MainActivity.this, Constants.ID_DISABLE);
                 TextView editDisableSleep = findViewById(R.id.editDisableSleep);
                 editDisableSleep.setEnabled(isChecked);

                 if (isChecked) {
                     sleepBroadcastReceiver.setAlarmDisableSleepMode(MainActivity.this);
                 } else {
                     displayToast("Auto Sleep Mode deactivation disabled");
                 }

                 SharedPreferences s = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                 SharedPreferences.Editor editor = s.edit();
                 editor.putBoolean(Constants.AUTOMATIC_DISABLE, isChecked);
                 editor.apply();
                 updateNextDay();
             }
        });

        // Time picker for enabling sleep
        final TextView editEnableSleep = findViewById(R.id.editEnableSleep);
        editEnableSleep.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String current = editEnableSleep.getText().toString();
                int h = Integer.parseInt(current.split(":")[0]);
                int m = Integer.parseInt(current.split(":")[1]);

                new TimePickerDialog(MainActivity.this, new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                        editEnableSleep.setText(String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute));
                        saveClocks();
                        updateNextDay();
                        sleepBroadcastReceiver.cancelAlarm(MainActivity.this, Constants.ID_ENABLE);
                        sleepBroadcastReceiver.setAlarmEnableSleepMode(MainActivity.this);
                    }
                }, h, m, true).show();
            }
        });

        // Time picker for disabling sleep
        final TextView editDisableSleep = findViewById(R.id.editDisableSleep);
        editDisableSleep.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String current = editDisableSleep.getText().toString();
                int h = Integer.parseInt(current.split(":")[0]);
                int m = Integer.parseInt(current.split(":")[1]);

                new TimePickerDialog(MainActivity.this, new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                        editDisableSleep.setText(String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute));
                        saveClocks();
                        updateNextDay();
                        sleepBroadcastReceiver.cancelAlarm(MainActivity.this, Constants.ID_DISABLE);
                        sleepBroadcastReceiver.setAlarmDisableSleepMode(MainActivity.this);
                    }
                }, h, m, true).show();
            }
        });

        // Initialize UI from settings
        editEnableSleep.setText(settings.getString(Constants.ENABLE_SLEEP_TIME, "23:00"));
        editDisableSleep.setText(settings.getString(Constants.DISABLE_SLEEP_TIME, "08:00"));
        switchEnableSleep.setChecked(settings.getBoolean(Constants.AUTOMATIC_ENABLE, false));
        switchDisableSleep.setChecked(settings.getBoolean(Constants.AUTOMATIC_DISABLE, false));
        
        editEnableSleep.setEnabled(switchEnableSleep.isChecked());
        editDisableSleep.setEnabled(switchDisableSleep.isChecked());
        updateNextDay();
    }

    private void displayToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.settings, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Matches the IDs in your menu/settings.xml
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStop() {
        super.onStop();
        saveClocks();
    }

    private void updateNextDay() {
        final TextView editEnableSleep = findViewById(R.id.editEnableSleep);
        final TextView editDisableSleep = findViewById(R.id.editDisableSleep);
        final TextView nextDay = findViewById(R.id.nextDay);
        
        try {
            String[] e = editEnableSleep.getText().toString().split(":");
            String[] d = editDisableSleep.getText().toString().split(":");
            int eHour = Integer.parseInt(e[0]);
            int eMinute = Integer.parseInt(e[1]);
            int dHour = Integer.parseInt(d[0]);
            int dMinute = Integer.parseInt(d[1]);

            if ((dHour < eHour) || (dHour == eHour && dMinute < eMinute)) {
                nextDay.setVisibility(View.VISIBLE);
            } else {
                nextDay.setVisibility(View.INVISIBLE);
            }
        } catch (Exception ex) {
            Log.e(TAG, "Error calculating next day visibility");
        }
    }

    private void saveClocks() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = settings.edit();
        final TextView editEnableSleep = findViewById(R.id.editEnableSleep);
        final TextView editDisableSleep = findViewById(R.id.editDisableSleep);
        editor.putString(Constants.ENABLE_SLEEP_TIME, editEnableSleep.getText().toString());
        editor.putString(Constants.DISABLE_SLEEP_TIME, editDisableSleep.getText().toString());
        editor.apply();
    }
}
