package dev.stickbit.speedleaderboard;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

public class MenuActivity extends AppCompatActivity {
    static MenuActivity a;

    @Override
    protected void onResume() {
        super.onResume();
        a = this;
        RegisterActivity.checkStarted(this);
        if (RoadDiscoveryActivity.backupDb != null) {
            Button b = findViewById(R.id.roaddiscoverybutton);
            Button b2 = findViewById(R.id.drivemodebutton);
            b.setText(R.string.restoringFilters);
            b2.setText(R.string.restoringFilters);
            b.setEnabled(false);
            b2.setEnabled(false);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    RoadDiscoveryActivity.loadedRecords = new HashMap<>();
                    RoadDiscoveryActivity.loadedRecords.putAll(RoadDiscoveryActivity.backupDb);
                    RoadDiscoveryActivity.backupDb = null;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            b.setEnabled(true);
                            b.setText(R.string.roadDiscovButton);
                            b2.setEnabled(true);
                            b2.setText(R.string.speedDriveButton);
                        }
                    });
                }
            }).start();
        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);
        Context me = this;
        findViewById(R.id.roaddiscoverybutton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(me, RoadDiscoveryActivity.class));
            }
        });
        findViewById(R.id.switchUnits).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SharedPreferences settings = getSharedPreferences("settings", MODE_PRIVATE);
                SharedPreferences.Editor editor = settings.edit();
                if (((Button) view).getText().equals(getString(R.string.switchtoKMH))) {
                    editor.putBoolean("MPH", false);
                    RegisterActivity.MPH = false;
                    ((Button) view).setText(R.string.switchtoMPH);
                } else {
                    editor.putBoolean("MPH", true);
                    RegisterActivity.MPH = true;
                    ((Button) view).setText(R.string.switchtoKMH);
                }
                editor.apply();
            }
        });
        if (!RegisterActivity.MPH) {
            ((Button) findViewById(R.id.switchUnits)).setText(R.string.switchtoMPH);
        }
        findViewById(R.id.drivemodebutton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(me, DriveModeActivity.class));
            }
        });
        findViewById(R.id.leavegroupbutton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case DialogInterface.BUTTON_POSITIVE:
                                RegisterActivity.mDatabase.collection("groups").document(RegisterActivity.mAuth.getUid()).delete().addOnCompleteListener(new OnCompleteListener<Void>() {
                                    @Override
                                    public void onComplete(@NonNull @NotNull Task<Void> task) {
                                        finish();
                                        startActivity(new Intent(view.getContext(), RegisterActivity.class));
                                    }
                                });
                                break;

                            case DialogInterface.BUTTON_NEGATIVE:
                                //No button clicked
                                break;
                        }
                    }
                };
                AlertDialog.Builder builder = new MaterialAlertDialogBuilder(view.getContext());
                builder.setMessage(getString(R.string.leaveWarn)).setPositiveButton(R.string.leaveBtn, dialogClickListener).setNegativeButton(R.string.stayBtn, dialogClickListener).show();
            }
        });
        findViewById(R.id.timetrialleaderboard).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(view.getContext(), TimeTrialsList.class));
            }
        });
        ((TextView) findViewById(R.id.usergrouplabel)).setText(getText(R.string.loggedInAs) + RegisterActivity.userName + getText(R.string.inGroup) + RegisterActivity.groupName);

        SharedPreferences settings = getSharedPreferences("settings", MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        if (!settings.getBoolean("disclaimer", false)) {
            AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this, R.layout.activity_menu);
            LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View popup = inflater.inflate(R.layout.disclaimerpopup, null);
            builder.setView(popup);
            builder.setCancelable(false);
            Dialog a;
            a = builder.create();
            a.show();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(15000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            a.dismiss();
                            editor.putBoolean("disclaimer", true);
                            editor.apply();
                        }
                    });
                }
            }).start();
        }
        if (RegisterActivity.loadToRoads) {
            RegisterActivity.loadToRoads = false;
            findViewById(R.id.roaddiscoverybutton).callOnClick();
        }
    }

    @Override
    public void onBackPressed() {
        RegisterActivity.goToMenu = false;
        RegisterActivity.regAct.finish();
        finish();
    }
}