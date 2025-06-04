package com.example.androidapplication;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.RouteInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import android.content.SharedPreferences;

public class MainActivity extends AppCompatActivity {


    private TextView textViewStatus;
    private List<EditText> editTexts;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private final String esp32Ip = "192.168.4.1";
    private final String prefsKey = "ESP32_DATA";

    private final BroadcastReceiver wifiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateWifiStatus();
        }
    };


    private void updateWifiStatus() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return;
        android.net.Network network = cm.getActiveNetwork();
        LinkProperties linkProperties = cm.getLinkProperties(network);

        boolean isConnectedToESP32 = false;
        if (linkProperties != null) {
            for (RouteInfo route : linkProperties.getRoutes()) {
                if ("192.168.4.1".equals(route.getGateway() != null ? route.getGateway().getHostAddress() : null)) {
                    isConnectedToESP32 = true;
                    break;
                }
            }
        }

        final boolean finalIsConnectedToESP32 = isConnectedToESP32;
        runOnUiThread(() -> textViewStatus.setText(finalIsConnectedToESP32 ? "Status: Connected to Device" : "Status: Device not connected"));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) ->
                Log.e("CRASH", "Uncaught exception in thread " + thread.getName(), throwable)
        );

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        registerReceiver(wifiReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        Button buttonSend = findViewById(R.id.buttonSend);
        Button buttonClear = findViewById(R.id.buttonClear);
        textViewStatus = findViewById(R.id.textViewStatus);

        Button buttonWifi = findViewById(R.id.buttonWifi);
        buttonWifi.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS))
        );

        Button buttonSettings = findViewById(R.id.buttonSettings);
        buttonSettings.setOnClickListener(v -> showSettingsDialog());

        editTexts = new ArrayList<>();
        for (int id : new int[]{R.id.editText1, R.id.editText2, R.id.editText3, R.id.editText4, R.id.editText5,
                R.id.editText6, R.id.editText7, R.id.editText8, R.id.editText9, R.id.editText10,
                R.id.editText11, R.id.editText12, R.id.editText13, R.id.editText14, R.id.editText15}) {
            EditText editText = findViewById(id);
            editTexts.add(editText);
        }

        setupEditTexts();

        Button buttonTest = findViewById(R.id.buttonTest);
        buttonTest.setOnClickListener(v -> sendTestPattern());

        loadSavedData();
        setupSaveButtonWatcher();

        buttonSend.setOnClickListener(v -> sendData());
        buttonClear.setOnClickListener(v -> clearData());
    }

    //Wifi Settings
    //Shows a dialog for the user to enter new SSID/password.
    private void showSettingsDialog() {
        EditText newSsidInput = new EditText(this);
        newSsidInput.setHint("Enter New SSID");

        EditText newPasswordInput = new EditText(this);
        newPasswordInput.setHint("Enter New Password");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        layout.addView(newSsidInput);
        layout.addView(newPasswordInput);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("New Wi-Fi Credentials")
                .setView(layout)
                .setMessage("Please enter new SSID and Password. Click OK to continue.")
                .setPositiveButton("OK", (dialog, which) -> {
                    String newSsid = newSsidInput.getText().toString().trim();
                    String newPassword = newPasswordInput.getText().toString().trim();

                    if (!newSsid.isEmpty() && !newPassword.isEmpty()) {
                        // Step: Show confirmation popup
                        showConfirmPopup(newSsid, newPassword);
                    } else {
                        Toast.makeText(this, "Please enter both SSID and Password", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Show a confirmation dialog before sending
    private void showConfirmPopup(String ssid, String password) {
        String message = "Do you want to update the following Wi-Fi credentials?\n\n"
                + "SSID: " + ssid + "\n"
                + "Password: " + password;

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Confirm Wi-Fi Update")
                .setMessage(message)
                .setPositiveButton("Confirm", (dialog, which) -> confirmWifiCredentials(ssid, password))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Send SSID and password to ESP32
    private void confirmWifiCredentials(String newssid, String newpassword) {
        String configUrl = "http://" + esp32Ip + "/setwifi?ssid=" + newssid + "&pass=" + newpassword;

        Request configRequest = new Request.Builder().url(configUrl).build();
        client.newCall(configRequest).enqueue(new BaseCallback() {
            @Override
            public void onSuccess(Response response) {
                runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Success")
                        .setMessage("Wi-Fi credentials updated successfully.\n\nSSID: " + newssid + "\nPassword: " + newpassword)
                        .setPositiveButton("OK", null)
                        .show());

            }

            @Override
            public void onHttpError(Response response) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error: " + response.code(), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onFailureHandled(IOException e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }


    private void setupEditTexts() {
        for (int i = 1; i < editTexts.size(); i++) {
            EditText editText = editTexts.get(i);
            editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(5)});
            editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    String text = s.toString();
                    if (text.length() == 5 && !text.contains(".")) {
                        Toast.makeText(MainActivity.this, "Enter valid values", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            });
        }

        EditText editText1 = editTexts.get(0);
        editText1.setInputType(InputType.TYPE_CLASS_NUMBER);
        editText1.addTextChangedListener(new TextWatcher() {
            private boolean isFormatting = false;

            @Override
            public void afterTextChanged(Editable s) {
                if (isFormatting || s == null) return;

                String clean = s.toString().replace(".", "");
                if (clean.length() > 6) return;

                isFormatting = true;

                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < clean.length(); i++) {
                    builder.append(clean.charAt(i));
                    if ((i == 1 || i == 3) && i != clean.length() - 1) {
                        builder.append('.');
                    }
                }

                editText1.setText(builder.toString());
                editText1.setSelection(builder.length());

                if (clean.length() >= 4) {
                    try {
                        int day = Integer.parseInt(clean.substring(0, 2));
                        int month = Integer.parseInt(clean.substring(2, 4));
                        if (day < 1 || day > 31 || month < 1 || month > 12) {
                            Toast.makeText(MainActivity.this, "Please enter a valid Date", Toast.LENGTH_SHORT).show();
                        }
                    } catch (NumberFormatException ignored) {}
                }
                isFormatting = false;
            }

            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });
    }

    private void sendTestPattern() {
        String url = "http://" + esp32Ip + "/test";
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new BaseCallback() {
            @Override
            public void onSuccess(Response response) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Test pattern sent!", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onHttpError(Response response) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Test HTTP Error: " + response.code(), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onFailureHandled(IOException e) {
                Log.e("HTTP", "Timeout or failure: " + e.getMessage(), e);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Connection error: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void sendData() {
        List<String> dataList = new ArrayList<>();
        String topValue = editTexts.get(0).getText().toString().trim();
        if (!topValue.isEmpty()) dataList.add("top=" + topValue);

        for (int i = 1; i < editTexts.size(); i++) {
            String value = editTexts.get(i).getText().toString().trim();
            if (!value.isEmpty()) {
                dataList.add("v" + i + "=" + value);
            }
        }

        if (dataList.isEmpty()) {
            Toast.makeText(this, "Enter at least one value", Toast.LENGTH_SHORT).show();
            return;
        }

        String url = "http://" + esp32Ip + "/update?" + String.join("&", dataList);
        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new BaseCallback() {
            @Override
            public void onSuccess(Response response) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Data sent and saved!", Toast.LENGTH_SHORT).show();
                    saveData();
                });
            }

            @Override
            public void onHttpError(Response response) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "HTTP Error: " + response.code(), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onFailureHandled(IOException e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to send: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void clearData() {
        for (EditText editText : editTexts) {
            editText.setText("");
        }

        String url = "http://" + esp32Ip + "/clear";
        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new BaseCallback() {
            @Override
            public void onSuccess(Response response) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Display cleared, loading saved data...", Toast.LENGTH_LONG).show();
                    loadSavedData();
                });
            }

            @Override
            public void onHttpError(Response response) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Clear HTTP Error: " + response.code(), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onFailureHandled(IOException e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Clear failed: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void setupSaveButtonWatcher() {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {}
        };
        for (EditText editText : editTexts) {
            editText.addTextChangedListener(watcher);
        }
    }

    private void saveData() {
        SharedPreferences sharedPrefs = getSharedPreferences(prefsKey, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.clear();
        for (int i = 0; i < editTexts.size(); i++) {
            String value = editTexts.get(i).getText().toString().trim();
            if (!value.isEmpty()) {
                editor.putString("v" + (i + 1), value);
            }
        }
        editor.apply();
    }

    private void loadSavedData() {
        SharedPreferences sharedPrefs = getSharedPreferences(prefsKey, MODE_PRIVATE);
        for (int i = 0; i < editTexts.size(); i++) {
            editTexts.get(i).setText(sharedPrefs.getString("v" + (i + 1), ""));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(wifiReceiver);
    }

    public abstract static class BaseCallback implements Callback {
        @Override
        public void onFailure(@NonNull Call call, @NonNull IOException e) {
            Log.e("HTTP", "Request failed: " + e.getLocalizedMessage(), e);
            onFailureHandled(e);
        }

        @Override
        public void onResponse(@NonNull Call call, Response response) {
            if (!response.isSuccessful()) {
                onHttpError(response);
            } else {
                onSuccess(response);
            }
        }

        public abstract void onSuccess(Response response);

        public void onFailureHandled(IOException e) {}

        public void onHttpError(Response response) {}
    }
}

