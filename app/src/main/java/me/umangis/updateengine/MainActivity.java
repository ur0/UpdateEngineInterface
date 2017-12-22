package me.umangis.updateengine;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ServiceManager;
import android.os.UpdateEngine;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    UpdateEngine ue;
    float progress;
    int status;
    ProgressBar progressBar;
    Button startButton, pauseButton, stopButton;
    boolean paused;
    private FirebaseAnalytics mFirebaseAnalytics;

    private static String[] getInfo() {
        return new String[]{
                "FILE_HASH=o6jHHKAyfn9s2U38EzUYjhwzKMpZYzr3mEJwwdKYtNA=",
                "FILE_SIZE=1160314050",
                "METADATA_HASH=p7kFWoD8Hm7tXkdSerAaBukIO/wKKo3lmMQav++9uF0=",
                "METADATA_SIZE=91224"

        };
    }

    protected void onCreate(Bundle savedInstanceState) {
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        addToDB();

        if (!ensureSystem())
            return;

        progressBar = findViewById(R.id.pb);
        progressBar.setMax(100);

        startButton = findViewById(R.id.start);
        pauseButton = findViewById(R.id.pause);
        stopButton = findViewById(R.id.stop);

        paused = false;

        setupButtonListeners();

        UECallback cb = new UECallback();
        ue = new UpdateEngine();
        ue.bind(cb);
    }

    private void setupButtonListeners() {
        startButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                ue.applyPayload("https://storage.googleapis.com/ur0-tissot-oreo/payload_800_2.bin",
                        0, 1160314050,
                        getInfo());
                pauseButton.setVisibility(View.VISIBLE);
                stopButton.setVisibility(View.VISIBLE);
                startButton.setVisibility(View.INVISIBLE);
            }
        });

        pauseButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (paused) {
                    paused = false;
                    ue.resume();
                    pauseButton.setText(R.string.pause);
                    return;
                }

                paused = true;
                pauseButton.setText(R.string.resume);
                ue.suspend();
            }
        });

        stopButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                ue.cancel();
                pauseButton.setVisibility(View.INVISIBLE);
                stopButton.setVisibility(View.INVISIBLE);
                startButton.setVisibility(View.VISIBLE);
                paused = false;
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case 1:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    try {
                        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
                        FirebaseFirestore db = FirebaseFirestore.getInstance();
                        Map<String, String> user = new HashMap<>();
                        String deviceId;
                        try {
                            deviceId = telephonyManager.getDeviceId(0);
                        } catch (NullPointerException e) {
                            deviceId = "";
                        }
                        user.put("device_id", deviceId);
                        db.collection("users").add(user)
                                .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                                    @Override
                                    public void onSuccess(DocumentReference documentReference) {
                                        Log.d("UEI", "DocumentSnapshot added with ID: " + documentReference.getId());
                                    }
                                })
                                .addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        Log.w("UEI", "Error adding document", e);
                                    }
                                });
                    } catch (SecurityException e) {
                        Toast.makeText(MainActivity.this, "Please grant the required permissions to continue", Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(MainActivity.this, "Permission Denied!", Toast.LENGTH_SHORT).show();
                }
        }
    }

    private void addToDB() {
        SharedPreferences preferences = getApplicationContext().getSharedPreferences("db", 0);

        if (preferences.getBoolean("saved", false))
            return;

        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("saved", true);
        editor.apply();

        if (ContextCompat.checkSelfPermission(MainActivity.this, "android.permission.READ_PHONE_STATE")
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(MainActivity.this, "The following permissions are required to generate a unique ID for your device, " +
                    "in order to prevent abuse.", Toast.LENGTH_LONG).show();
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{"android.permission.READ_PHONE_STATE", "android.permission.GET_ACCOUNTS"}, 1);
        }

    }

    private boolean ensureSystem() {
        IBinder s = ServiceManager.getService("android.os.UpdateEngineService");
        if (s == null) {
            Log.e("UEI", "Can't get IBinder, not running as a system app!");
            Toast.makeText(MainActivity.this, "Install this as a system app to continue",
                    Toast.LENGTH_LONG).show();
            finishAndRemoveTask();
            return false;
        }
        return true;
    }

    private class UECallback extends android.os.UpdateEngineCallback {
        public void onStatusUpdate(int aStatus, float aPercent) {
            progress = aPercent * 100;
            status = aStatus;
            progressBar.setProgress(Math.round(progress), true);

            Bundle params = new Bundle();
            params.putFloat("install_percent", aPercent);
            mFirebaseAnalytics.logEvent("install_progress", params);

            if (aStatus > 0)
                findViewById(R.id.stop).setVisibility(View.VISIBLE);

            Log.w("UEI", "Status: " + Integer.toString(status) +
                    ", " + Float.toString(progress) + "% done.");
        }

        public void onPayloadApplicationComplete(int errCode) {
            Log.w("UEI", "Payload application complete, error: " + Integer.toString(errCode));

            Bundle params = new Bundle();
            params.putInt("error_code", errCode);
            mFirebaseAnalytics.logEvent("install_result", params);

            if (errCode == 0) {
                Log.w("UEI", "Installation succeeded!");
                findViewById(R.id.completed).setVisibility(View.VISIBLE);
            }
        }
    }
}
