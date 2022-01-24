package dev.stickbit.speedleaderboard;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.SetOptions;
import org.jetbrains.annotations.NotNull;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class DriveModeActivity extends AppCompatActivity {
    @SuppressLint("StaticFieldLeak") //don't care didn't ask
    public static DriveModeActivity mainAct;
    public static Intent serviceI;
    public TextView speedDisp, roadNameTTTimeDisp, newRecordsCheckpointLabel;
    boolean serviceStarted = false;
    MapView map;
    boolean weHavePermission = false;
    boolean firstRun = true;
    long timeWeAsked;
    boolean reset = true;
    Switch mapFollow;
    CountDownLatch waitForProcess;

    public static Drawable getBitmapFromVectorDrawable(Context context, int drawableId) {
        Drawable drawable = ContextCompat.getDrawable(context, drawableId);
        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return new BitmapDrawable(bitmap);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        RegisterActivity.checkStarted(this);
        super.onCreate(savedInstanceState);
        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        setContentView(R.layout.activity_driving);

        map = findViewById(R.id.driveModeMap);
        map.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE);
        map.setMultiTouchControls(true);
        map.setTilesScaledToDpi(false);
        //    map.getController().setCenter(new GeoPoint(44.738434, -63.304218));
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        map.getController().setZoom(5.3 * metrics.density);


        MyLocationNewOverlay mLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), map);
        mLocationOverlay.runOnFirstFix(new Runnable() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mLocationOverlay.enableMyLocation();
                        mLocationOverlay.enableFollowLocation();
                        map.getController().setCenter(mLocationOverlay.getMyLocation());
                        map.getController().animateTo(mLocationOverlay.getMyLocation());
                    }
                });
            }
        });
        mLocationOverlay.setEnableAutoStop(false);
        map.getOverlays().add(mLocationOverlay);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        checkPermission(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_BACKGROUND_LOCATION}, 1);
                        if (savedInstanceState != null) {
                            serviceStarted = savedInstanceState.getBoolean("service", false);
                        }
                        if (!serviceStarted && weHavePermission) {
                            serviceI = new Intent(ctx, GPSManagerService.class);
                            startForegroundService(serviceI);
                            serviceStarted = true;
                        }
                        firstRun = false;
                    }
                });
            }
        }).start();
        mapFollow = findViewById(R.id.mapFollowSwitch);
        mapFollow.setChecked(true);
        mapFollow.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b) {
                    mLocationOverlay.enableFollowLocation();
                } else mLocationOverlay.disableFollowLocation();
            }
        });
        mapFollow.bringToFront();
        findViewById(R.id.osmLabel).bringToFront();
        if (Boolean.parseBoolean(getString(R.string.isNight))) {
            ColorMatrix inverseMatrix = new ColorMatrix(new float[]{-1.0f, 0.0f, 0.0f, 0.0f, 255f, 0.0f, -1.0f, 0.0f, 0.0f, 255f, 0.0f, 0.0f, -1.0f, 0.0f, 255f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f});

            int destinationColor = Color.parseColor("#FF2A2A2A");
            float lr = (255.0f - Color.red(destinationColor)) / 255.0f;
            float lg = (255.0f - Color.green(destinationColor)) / 255.0f;
            float lb = (255.0f - Color.blue(destinationColor)) / 255.0f;
            ColorMatrix grayscaleMatrix = new ColorMatrix(new float[]{lr, lg, lb, 0, 0, //
                    lr, lg, lb, 0, 0, //
                    lr, lg, lb, 0, 0, //
                    0, 0, 0, 0, 255, //
            });
            grayscaleMatrix.preConcat(inverseMatrix);
            int dr = Color.red(destinationColor);
            int dg = Color.green(destinationColor);
            int db = Color.blue(destinationColor);
            float drf = dr / 255f;
            float dgf = dg / 255f;
            float dbf = db / 255f;
            ColorMatrix tintMatrix = new ColorMatrix(new float[]{drf, 0, 0, 0, 0, //
                    0, dgf, 0, 0, 0, //
                    0, 0, dbf, 0, 0, //
                    0, 0, 0, 1, 0, //
            });
            tintMatrix.preConcat(grayscaleMatrix);
            float lDestination = drf * lr + dgf * lg + dbf * lb;
            float scale = 1f - lDestination;
            float translate = 1 - scale * 0.5f;
            ColorMatrix scaleMatrix = new ColorMatrix(new float[]{scale, 0, 0, 0, dr * translate, //
                    0, scale, 0, 0, dg * translate, //
                    0, 0, scale, 0, db * translate, //
                    0, 0, 0, 1, 0, //
            });
            scaleMatrix.preConcat(tintMatrix);
            ColorMatrixColorFilter filter = new ColorMatrixColorFilter(scaleMatrix);
            map.getOverlayManager().getTilesOverlay().setColorFilter(filter);
        }
        speedDisp = findViewById(R.id.speedLabelDMode);
        roadNameTTTimeDisp = findViewById(R.id.roadNameTimerDMode);
        newRecordsCheckpointLabel = findViewById(R.id.newRecordsCheckpointsLeftLabel);
        newRecordsCheckpointLabel.setText(getText(R.string.recordsThisSesh) + "0");
        findViewById(R.id.exitDModeButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                waitForProcess = new CountDownLatch(1);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            waitForProcess.await();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        Map<String, Map<String, Map<String, String>>> finalDb = new HashMap<>();
                        finalDb.put("records", GPSManagerService.recordsToUpload);
                        RegisterActivity.mDatabase.collection("users").document(RegisterActivity.groupName).set(finalDb, SetOptions.merge()).addOnSuccessListener(new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void unused) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        finish();
                                        if (MenuActivity.a != null) MenuActivity.a.finish();
                                        RegisterActivity.loadToRoads = true;
                                        Toast.makeText(ctx, R.string.recordsUpload, Toast.LENGTH_LONG).show();
                                        startActivity(new Intent(ctx, RegisterActivity.class));
                                    }
                                });
                            }
                        });
                    }
                }).start();
                stopService(serviceI);
                view.setEnabled(false);
                ((Button) view).setText(R.string.uploading);
            }
        });
        Marker m = new Marker(map);
        m.setIcon(resize(getBitmapFromVectorDrawable(this, R.drawable.start_point_marker_foreground)));
        m.setPosition(new GeoPoint(30.629566, -97.673069));
        m.setTitle(getString(R.string.startPoint));
        m.setSubDescription(getString(R.string.startPointDesc));
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        map.getOverlays().add(m);

        Marker m1 = new Marker(map);
        m1.setIcon(resize(getBitmapFromVectorDrawable(this, R.drawable.next_checkpoint_marker_foreground)));
        m1.setPosition(new GeoPoint(30.630188, -97.673122));
        m1.setTitle(getString(R.string.nextCheckpoint));
        m1.setSubDescription(getString(R.string.nextCheckpointDesc));
        m1.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        map.getOverlays().add(m1);

        Marker m2 = new Marker(map);
        m2.setIcon(resize(getBitmapFromVectorDrawable(this, R.drawable.future_checkpoint_marker_foreground)));
        m2.setPosition(new GeoPoint(30.630721, -97.673096));
        m2.setTitle(getString(R.string.futureCheckpoint));
        m2.setSubDescription(getString(R.string.futreCheckpointDesc));
        m2.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        map.getOverlays().add(m2);

        Marker m3 = new Marker(map);
        m3.setIcon(resize(getBitmapFromVectorDrawable(this, R.drawable.finish_point_marker_foreground)));
        m3.setPosition(new GeoPoint(30.631301, -97.673096));
        m3.setTitle(getString(R.string.finishPoint));
        m3.setSubDescription(getString(R.string.finishPointDesc));
        m3.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        map.getOverlays().add(m3);


    }

    @Override
    public void onSaveInstanceState(@NotNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("service", serviceStarted);
    }

    private Drawable resize(Drawable image) {
        Bitmap b = ((BitmapDrawable) image).getBitmap();
        Bitmap bitmapResized = Bitmap.createScaledBitmap(b, 160, 160, false);
        return new BitmapDrawable(getResources(), bitmapResized);
    }

    @Override
    protected void onResume() {
        super.onResume();
        RegisterActivity.checkStarted(this);
        map.onResume();
        mainAct = this;
        if (!firstRun) {
            checkPermission(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        map.onPause();
        mainAct = null;
    }

    @Override
    public void onBackPressed() {
        Toast.makeText(this, R.string.backBtnDriveMode, Toast.LENGTH_LONG).show();
    }

    public void checkPermission(String[] permission, int requestCode) {
        List<String> perms = new LinkedList<>();
        for (String perm : permission) {
            if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_DENIED) {
                perms.add(perm);
            }
        }
        if (perms.size() > 0) {
            ActivityCompat.requestPermissions(this, perms.toArray(new String[0]), requestCode);
            timeWeAsked = System.currentTimeMillis();
        } else {
            weHavePermission = true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            if (grantResults.length <= 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.locationToast, Toast.LENGTH_LONG).show();
                if (System.currentTimeMillis() - timeWeAsked < 300) {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                }
            } else {
                weHavePermission = true;
            }
        }
    }


}