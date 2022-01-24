package dev.stickbit.speedleaderboard;

import android.Manifest;
import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.IBinder;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CountDownLatch;

public class GPSManagerService extends Service {

    public static Map<String, Integer> mySpeeds;
    public static Map<String, Map<String, String>> recordsToUpload;
    Location oldPoint;
    long timeSinceLastStreetLookup;
    boolean streetLookupInProg;
    CountDownLatch waitForStreet;
    Context svc;
    RequestQueue queue;
    List<String> newAndRecord;
    LocationCallback callback;
    private FusedLocationProviderClient fusedLocationClient;

    public GPSManagerService() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }


    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");

    }

    @Override
    public void onCreate() {
        svc = this;
        super.onCreate();
        if (!RegisterActivity.startedProperly) return;
        queue = Volley.newRequestQueue(getApplicationContext());
        mySpeeds = new HashMap<>();
        Map<String, Map<String, String>> dayta = (Map<String, Map<String, String>>) RegisterActivity.savedDB.get("records");
        for (String uid : dayta.keySet()) {
            Map<String, String> record = dayta.get(uid);
            if (record.get("user").equals(RegisterActivity.userName)) {
                mySpeeds.put(record.get("roadID"), (int) Double.parseDouble(record.get("speedRecord")));
            }
        }
        System.out.println("check me pls");
        newAndRecord = new LinkedList<>();
        try {
            CharSequence name = getString(R.string.servicechannelname);
            String description = getString(R.string.servicechanneldesc);
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel("ServiceChannel", name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        } catch (Exception ignored) {

        }


        Intent notificationIntent = new Intent(this, DriveModeActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new Notification.Builder(this, "ServiceChannel").setContentTitle(getText(R.string.servicechannelname)).setContentText(getText(R.string.drivemoderunning)).setSmallIcon(R.drawable.ic_launcher_foreground).setContentIntent(pendingIntent).setTicker(getText(R.string.servicechannelname)).build();
        startForeground(1, notification);
        final int[] timer = {0};
        new Thread(() -> {

        /*    while (true) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
                timer[0]++;

                Activity mainAct = MainActivity.mainAct;
                if(mainAct==null)
                    continue;
                mainAct.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView t = mainAct.findViewById(R.id.myText);
                        t.setText("Timer is at " + timer[0]);
                    }
                });
            }
        */
        }).start();
        LocationRequest request = LocationRequest.create();
        request.setFastestInterval(1);
        request.setInterval(750);
        request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }


        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        fusedLocationClient.requestLocationUpdates(request, callback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull @NotNull LocationResult locationResult) {
                super.onLocationResult(locationResult);
                if (DriveModeActivity.mainAct != null) {
                    DriveModeActivity.mainAct.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            DriveModeActivity.mainAct.newRecordsCheckpointLabel.setText(getText(R.string.recordsThisSesh) + (String.valueOf(newAndRecord.size())));
                        }
                    });
                }


                Location newPoint = locationResult.getLocations().get(locationResult.getLocations().size() - 1);
                if (oldPoint == null) {
                    oldPoint = newPoint;
                    return;
                }
                if (oldPoint.getAccuracy() > 50 && newPoint.getAccuracy() > 50) {
                    return;
                }
                if (oldPoint.getSpeed() == 0 || newPoint.getSpeed() == 0) {
                    if (DriveModeActivity.mainAct != null) {
                        DriveModeActivity.mainAct.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                String speedUnit = " km/h";
                                if (RegisterActivity.MPH) {
                                    speedUnit = " mph";
                                }
                                DriveModeActivity.mainAct.speedDisp.setText((getText(R.string.currentSpeed)) + "0" + speedUnit);
                            }
                        });
                    }
                    return;
                }
                try {
                    if (!streetLookupInProg && System.currentTimeMillis() - timeSinceLastStreetLookup > 10000) {
                        try {
                            waitForStreet = new CountDownLatch(1);
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    String coord1 = String.valueOf(newPoint.getLatitude());
                                    String coord2 = String.valueOf(newPoint.getLongitude());
                                    streetLookupInProg = true;
                                    try {
                                        new Thread(new Runnable() {
                                            @Override
                                            public void run() {
                                                RoadDiscoveryActivity.readStreets(new String[]{coord1, coord2}, null, svc, queue, null, null, waitForStreet);
                                            }
                                        }).start();
                                        waitForStreet.await();
                                        if (DriveModeActivity.mainAct != null) {
                                            DriveModeActivity.mainAct.runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    DriveModeActivity.mainAct.roadNameTTTimeDisp.setText(RoadDiscoveryActivity.newStreetName);
                                                    if (RoadDiscoveryActivity.newStreetID == null) {
                                                        DriveModeActivity.mainAct.roadNameTTTimeDisp.setText(R.string.noRoad);
                                                    }
                                                }
                                            });
                                        }
                                        timeSinceLastStreetLookup = System.currentTimeMillis();
                                        streetLookupInProg = false;
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }).start();
                        } catch (Exception e) {
                        }
                    }
                } catch (ConcurrentModificationException e) {
                }
                double speed = (oldPoint.getSpeed() + newPoint.getSpeed()) * 1.8d;
                if (speed > 100) {
                    RoadDiscoveryActivity.range = 30;
                } else RoadDiscoveryActivity.range = 15;
                if (RoadDiscoveryActivity.newStreetID != null) {
                    if (mySpeeds.containsKey(RoadDiscoveryActivity.newStreetID)) {
                        if (mySpeeds.get(RoadDiscoveryActivity.newStreetID) < speed) {
                            if (!newAndRecord.contains(RoadDiscoveryActivity.newStreetID))
                                newAndRecord.add(RoadDiscoveryActivity.newStreetID);
                            mySpeeds.put(RoadDiscoveryActivity.newStreetID, (int) speed);
                        }
                    } else {
                        newAndRecord.add(RoadDiscoveryActivity.newStreetID);
                        mySpeeds.put(RoadDiscoveryActivity.newStreetID, (int) speed);
                    }
                }


                String speedUnit = " km/h";
                long speedNum;
                if (RegisterActivity.MPH) {
                    speedUnit = " mph";
                    speed = speed * .62137119224;
                }
                speedNum = Math.round(speed);
                if (DriveModeActivity.mainAct != null) {
                    String finalSpeedUnit = speedUnit;
                    DriveModeActivity.mainAct.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            DriveModeActivity.mainAct.speedDisp.setText(((String) getText(R.string.currentSpeed)) + speedNum + finalSpeedUnit);
                        }
                    });
                }
                oldPoint = newPoint;
            }
        }, Looper.myLooper());
    }

    @Override
    public void onDestroy() {
        fusedLocationClient.removeLocationUpdates(callback);
        Map<String, Integer> originalSpeeds = new HashMap<>();
        Map<String, Map<String, String>> recordData = (Map<String, Map<String, String>>) RegisterActivity.savedDB.get("records");
        Map<String, Map<String, String>> newRecords = new HashMap<>();
        for (String uid : recordData.keySet()) {
            Map<String, String> record = recordData.get(uid);
            if (record.get("user").equals(RegisterActivity.userName)) {
                originalSpeeds.put(record.get("roadID"), (int) Double.parseDouble(record.get("speedRecord")));
            }
        }
        for (String roadID : mySpeeds.keySet()) {
            boolean added = false;
            for (String recordUID : recordData.keySet()) {
                Map<String, String> indivRecord = recordData.get(recordUID);
                if (indivRecord.get("user").equals(RegisterActivity.userName)) {
                    if (indivRecord.get("roadID").equals(roadID)) {
                        added = true;
                        Map<String, String> newRecord = new HashMap<>();
                        newRecord.put("user", RegisterActivity.userName);
                        newRecord.put("speedRecord", String.valueOf(mySpeeds.get(roadID)));
                        newRecord.put("firstTime", indivRecord.get("firstTime"));
                        newRecord.put("speedRecordTime", String.valueOf(System.currentTimeMillis()));
                        newRecord.put("roadID", roadID);
                        newRecords.put(recordUID, newRecord);
                    }
                }
            }
            if (!added) {
                Map<String, String> newRecord = new HashMap<>();
                newRecord.put("user", RegisterActivity.userName);
                newRecord.put("speedRecord", String.valueOf(mySpeeds.get(roadID)));
                newRecord.put("firstTime", String.valueOf(System.currentTimeMillis()));
                newRecord.put("speedRecordTime", String.valueOf(System.currentTimeMillis()));
                newRecord.put("roadID", roadID);
                newRecords.put(String.valueOf(UUID.randomUUID()), newRecord);
            }
        }
        recordsToUpload = newRecords;
        DriveModeActivity.mainAct.waitForProcess.countDown();


        super.onDestroy();
    }
}