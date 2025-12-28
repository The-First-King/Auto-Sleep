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
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;

public class MainActivity extends Activity
{
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
                Log.e(TAG, "Exception :( " + e.getMessage());
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

        Switch switchApp = (Switch) findViewById(R.id.switchApp);
        switchApp.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                sleepBroadcastReceiver.cancelAlarms(MainActivity.this);
                if (isChecked) {
                    String message = sleepBroadcastReceiver.setAlarms(MainActivity.this);
                    if (!message.isEmpty()) {
                        displayToast(message);
                    }
                }

                TextView disableSleepText = (TextView) findViewById(R.id.disableSleepText);
                TextView editDisableSleep = (TextView) findViewById(R.id.editDisableSleep);
                TextView enableSleepText = (TextView) findViewById(R.id.enableSleepText);
                TextView editEnableSleep = (TextView) findViewById(R.id.editEnableSleep);
                TextView nextDay = (TextView) findViewById(R.id.nextDay);

                for (TextView t : Arrays.asList(disableSleepText, editDisableSleep, enableSleepText, editEnableSleep, nextDay)) {
                    t.setEnabled(isChecked);
                }

                SharedPreferences s = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                SharedPreferences.Editor editor = s.edit();
                editor.putBoolean(Constants.APP_IS_ENABLED, isChecked);
                editor.apply();
            }
        });

        final TextView editEnableSleep = (TextView) findViewById(R.id.editEnableSleep);
        editEnableSleep.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                new TimePickerDialog(MainActivity.this, new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                        editEnableSleep.setText(String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute));
                        updateNextDay();
                        saveClocks();
                        sleepBroadcastReceiver.cancelAlarms(MainActivity.this);
                        String message = sleepBroadcastReceiver.setAlarms(MainActivity.this);
                        if (!message.isEmpty()) {
                            displayToast(message);
                        }
                    }
                }, 23, 0, true).show();
            }
        });

        final TextView editDisableSleep = (TextView) findViewById(R.id.editDisableSleep);
        editDisableSleep.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                new TimePickerDialog(MainActivity.this, new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                        editDisableSleep.setText(String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute));
                        updateNextDay();
                        saveClocks();
                        sleepBroadcastReceiver.cancelAlarms(MainActivity.this);
                        String message = sleepBroadcastReceiver.setAlarms(MainActivity.this);
                        if (!message.isEmpty()) {
                            displayToast(message);
                        }
                    }
                }, 8, 0, true).show();
            }
        });

        editEnableSleep.setText(settings.getString(Constants.ENABLE_SLEEP_TIME, "23:00"));
        editDisableSleep.setText(settings.getString(Constants.DISABLE_SLEEP_TIME, "08:00"));
        updateNextDay();

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
        switch (item.getItemId()) {
            case R.id.menu_about:
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(getString(R.string.about_description_main) + "\n\n" + getString(R.string.about_description_sleep_well))
                        .setCancelable(true)
                        .setPositiveButton(R.string.about_ok_button, null).show();
                break;
            case R.id.menu_settings:
                Intent aboutIntent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(aboutIntent);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStop() {
        super.onStop();
        saveClocks();
    }

    private void updateNextDay() {
        final TextView editEnableSleep = (TextView) findViewById(R.id.editEnableSleep);
        final TextView editDisableSleep = (TextView) findViewById(R.id.editDisableSleep);
        final TextView nextDay = (TextView) findViewById(R.id.nextDay);
        String enable = editEnableSleep.getText().toString();
        String disable = editDisableSleep.getText().toString();
        String[] e = enable.split(":");
        String[] d = disable.split(":");
        int eHour = Integer.valueOf(e[0]);
        int eMinute = Integer.valueOf(e[1]);
        int dHour = Integer.valueOf(d[0]);
        int dMinute = Integer.valueOf(d[1]);
        if ((dHour < eHour) || (dHour == eHour && dMinute < eMinute)) {
            nextDay.setVisibility(View.VISIBLE);
        } else {
            nextDay.setVisibility(View.INVISIBLE);
        }
    }

    private void saveClocks() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = settings.edit();
        final TextView editEnableSleep = (TextView) findViewById(R.id.editEnableSleep);
        final TextView editDisableSleep = (TextView) findViewById(R.id.editDisableSleep);
        editor.putString(Constants.ENABLE_SLEEP_TIME, editEnableSleep.getText().toString());
        editor.putString(Constants.DISABLE_SLEEP_TIME, editDisableSleep.getText().toString());
        Log.d(TAG, "enable sleep at: " + editEnableSleep.getText().toString());
        Log.d(TAG, "disable sleep at: " + editDisableSleep.getText().toString());
        editor.apply();
    }
}
