package dev.stickbit.speedleaderboard;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.android.volley.*;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CountDownLatch;

public class RoadDiscoveryActivity extends AppCompatActivity {
    public static String newStreetID;
    public static String newStreetName;
    static Map<String, Map<String, String>> loadedRecords;
    static boolean recordsFilled = false;
    static Map<String, Map<String, String>> backupDb;
    RequestQueue myQueue;
    static boolean sortSet = false;
    static String sortMethod;
    static boolean sortBackwards;
    List<Long> roads;
    TextView text;
    ViewGroup layout;
    boolean scrollToBottom;
    CountDownLatch waitForMapData;
    TextView filterBlurb;
    boolean weDidAnything;
    public static int range = 15;
    public static void readStreets(String[] location, Activity act, Context c, RequestQueue queue, TextView filterBlurb, CountDownLatch waitMap, CountDownLatch waitDrive) {
        final boolean[] mode = {false};
        if (location.length == 1) {
            mode[0] = true;
            act.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    filterBlurb.setText(R.string.mapsDownloading);
                }
            });
        }
        String url = "https://overpass-api.de/api/interpreter";
        StringRequest stringRequest = new StringRequest(Request.Method.POST, url, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                if (mode[0]) {
                    act.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            filterBlurb.setText(R.string.mapsImporting);
                        }
                    });
                }
                new Thread() {
                    @Override
                    public void run() {
                        Map output = (Map) new Gson().fromJson(response, Object.class);
                        List<Map> points = (List<Map>) output.get("elements");
                        int cnt = 0;
                        for (Map p : points) {
                            cnt++;
                            long id = (long) (Double.parseDouble(String.valueOf(p.get("id"))));
                            if (p.get("type").equals("way")) {
                                if (p.containsKey("tags")) {
                                    newStreetID = String.valueOf(id);
                                    if (((Map) p.get("tags")).containsKey("name")) {
                                        newStreetName = String.valueOf(((Map) p.get("tags")).get("name"));
                                    }
                                    else if (((Map) p.get("tags")).containsKey("ref")) {
                                        newStreetName = ((Map) p.get("tags")).get("ref") + " (No Name)";
                                    } else {
                                        newStreetName = "(Unnamed Road)";
                                    }

                                    RegisterActivity.streetNames.put(id, newStreetName);
                                }
                            }
                        }
                        if (cnt == 0) {
                            newStreetID = null;
                            newStreetName = null;
                        }
                        if (mode[0]) waitMap.countDown();
                        else waitDrive.countDown();

                    }
                }.start();

            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                if (mode[0]) act.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        filterBlurb.setText(String.valueOf(error));
                    }
                });
                newStreetID = null;
                newStreetName = null;
                if(waitMap!=null)
                    waitMap.countDown();
                if(waitDrive!=null)
                    waitDrive.countDown();
                //loadBtn.setEnabled(true);


            }

        }) {
            @Override
            public byte[] getBody() throws AuthFailureError {
                String msg = "";
                if (mode[0]) {
                    long area = 3600000000L + Long.parseLong(location[0]);
                    msg = "[out:json];\n" + "area(" + area + ");\n" + "(\n" + "way(area)\n" + "['highway']\n" + "['highway' !~ 'path']\n" + "['highway' !~ 'steps']\n" + "['highway' !~ 'motorway_link']\n['highway' !~ 'motorway_junction']\n" + "['highway' !~ 'raceway']\n" + "['highway' !~ 'bridleway']\n" + "['highway' !~ 'proposed']\n" + "['highway' !~ 'construction']\n" + "['highway' !~ 'elevator']\n" + "['highway' !~ 'bus_guideway']\n" + "['highway' !~ 'footway']\n" + "['highway' !~ 'cycleway']\n" + "['foot' !~ 'no']\n" + "['access' !~ 'private']\n" + "['access' !~ 'no'];\n" + ");\n" + "(._;>;);\n" + "out;";
                } else {
                    msg = "[out:json];\n" + "is_in(" + location[0] + ", " + location[1] + ");\n" + "(\n" + "way(around:"+range+"," + location[0] + ", " + location[1] + ")\n" + "['highway']\n" + "['highway' !~ 'path']\n" + "['highway' !~ 'steps']\n" + "['highway' !~ 'motorway_link']\n['highway' !~ 'motorway_junction']\n" + "['highway' !~ 'raceway']\n" + "['highway' !~ 'bridleway']\n" + "['highway' !~ 'proposed']\n" + "['highway' !~ 'construction']\n" + "['highway' !~ 'elevator']\n" + "['highway' !~ 'bus_guideway']\n" + "['highway' !~ 'footway']\n" + "['highway' !~ 'cycleway']\n" + "['foot' !~ 'no']\n" + "['access' !~ 'private']\n" + "['access' !~ 'no'];\n" + ");\n" + "(._;>;);\n" + "out;";
                    System.out.println("Asking:\n" + msg);
                }
                try {
                    msg = URLEncoder.encode(msg, String.valueOf(StandardCharsets.UTF_8));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                return ("data=" + msg).getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public String getBodyContentType() {
                return "application/x-www-form-urlencoded";
            }
        };
        stringRequest.setRetryPolicy(new DefaultRetryPolicy(999999999, 0, 0));
        queue.add(stringRequest);
    }

    @Override
    protected void onResume() {
        super.onResume();
        RegisterActivity.checkStarted(this);
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    void cleanDB(List<String> IDs) {
        if (backupDb == null) {
            backupDb = new HashMap<>();
            backupDb.putAll(loadedRecords);
        }
        loadedRecords.entrySet().removeIf(entry -> !IDs.contains(entry.getKey()));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        range =15;
        myQueue = Volley.newRequestQueue(this);
        setContentView(R.layout.activity_road_discovery);
        scrollToBottom = false;
        if (!sortSet) {
            sortMethod = "speedRecord";
            sortBackwards = true;
            sortSet = true;
        }
       /* loadBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //    province = statePicker.getSelectedItem().toString();
                //  city = cityEdit.getText().toString();
                //country = countryPicker.getSelectedItem().toString();
                try {
                    province = URLEncoder.encode(province, String.valueOf(StandardCharsets.UTF_8));
                    city = URLEncoder.encode(city, String.valueOf(StandardCharsets.UTF_8));
                    country = URLEncoder.encode(country, String.valueOf(StandardCharsets.UTF_8));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                view.setEnabled(false);
                findIDOfCity();
            }
        });*/
        Context c = this;
        //if (!recordsFilled) fillRecords();
        findViewById(R.id.sortbutton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog.Builder builder = new MaterialAlertDialogBuilder(view.getContext(), R.layout.activity_road_discovery);
                LayoutInflater inflater = (LayoutInflater) view.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                View bubbles = inflater.inflate(R.layout.sort_dialog, null);
                RadioGroup bubbleGroup = bubbles.findViewById(R.id.sortButtonsGroup);
                final AlertDialog[] a = new AlertDialog[]{null};
                if (sortMethod == null) {
                    sortMethod = "speedRecord";
                }
                switch (sortMethod) {
                    case "speedRecord": {
                        ((RadioButton) bubbleGroup.findViewById(R.id.sortBySpeed)).toggle();
                        break;
                    }
                    case "speedRecordTime": {
                        ((RadioButton) bubbleGroup.findViewById(R.id.sortByRecentSpeed)).toggle();
                        break;
                    }
                    case "firstTime": {
                        ((RadioButton) bubbleGroup.findViewById(R.id.sortByNewRoad)).toggle();
                        break;
                    }
                    case "roadID": {
                        ((RadioButton) bubbleGroup.findViewById(R.id.sortByRoad)).toggle();
                        break;
                    }
                    case "user": {
                        ((RadioButton) bubbleGroup.findViewById(R.id.sortByName)).toggle();
                        break;
                    }
                }


                bubbleGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(RadioGroup radioGroup, int i) {
                        String sort = String.valueOf(((RadioButton) radioGroup.findViewById(i)).getText());
                        if (sort.equals(getString(R.string.speed))) {
                            sortMethod = "speedRecord";
                        }
                        if (sort.equals(getString(R.string.mostRecentNew))) {
                            sortMethod = "firstTime";
                        }
                        if (sort.equals(getString(R.string.mostRecentSpeed))) {
                            sortMethod = "speedRecordTime";
                        }
                        if (sort.equals(getString(R.string.sortRoad))) {
                            sortMethod = "roadID";
                        }
                        if (sort.equals(getString(R.string.sortName))) {
                            sortMethod = "user";
                        }
                        a[0].dismiss();
                        LinearLayout v = findViewById(R.id.leaderboardScollyThing);
                        v.removeAllViews();
                        loadPages(0, c);
                    }
                });
                builder.setView(bubbles);
                builder.setCancelable(false);
                builder.setTitle(R.string.sort_by);
                a[0] = builder.create();
                a[0].show();
                bubbles.findViewById(R.id.reverseOrder).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        sortBackwards = !sortBackwards;
                        a[0].dismiss();
                        LinearLayout v = findViewById(R.id.leaderboardScollyThing);
                        v.removeAllViews();
                        loadPages(0, c);
                    }
                });

            }
        });


        findViewById(R.id.filterButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final AlertDialog[] a = new AlertDialog[1];
                AlertDialog.Builder builder = new MaterialAlertDialogBuilder(view.getContext(), R.layout.activity_road_discovery);
                LayoutInflater inflater = (LayoutInflater) view.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                View bubbles = inflater.inflate(R.layout.filter_dialog, null);
                builder.setView(bubbles);
                builder.setCancelable(false);
                builder.setTitle(R.string.filter);
                a[0] = builder.create();
                a[0].show();
                Spinner statePicker = bubbles.findViewById(R.id.statepicker2);
                Spinner countryPicker = bubbles.findViewById(R.id.countrypicker2);
                countryPicker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                        ArrayAdapter<CharSequence> adapter;
                        if (adapterView.getItemAtPosition(i).toString().equals("Canada")) {
                            adapter = ArrayAdapter.createFromResource(countryPicker.getContext(), R.array.provinces, android.R.layout.simple_spinner_item);
                        } else if (adapterView.getItemAtPosition(i).toString().equals("United States of America")) {
                            adapter = ArrayAdapter.createFromResource(countryPicker.getContext(), R.array.states, android.R.layout.simple_spinner_item);
                        } else {
                            adapter = ArrayAdapter.createFromResource(countryPicker.getContext(), R.array.pickCountryFirst, android.R.layout.simple_spinner_item);
                        }
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                        statePicker.setAdapter(adapter);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> adapterView) {

                    }
                });
                final boolean[] userExact = new boolean[1];
                final boolean[] streetExact = new boolean[1];
                ((Switch) bubbles.findViewById(R.id.usernameExactMatchSwitch)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                        userExact[0] = b;
                    }
                });
                ((Switch) bubbles.findViewById(R.id.streetNameExactMatchSwitch)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                        streetExact[0] = b;
                    }
                });

                bubbles.findViewById(R.id.gofilterbutton).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        filterBlurb = bubbles.findViewById(R.id.filterdialogblurb);
                        filterBlurb.setText(R.string.filtering);
                        view.setEnabled(false);
                        String username, street, city;
                        String province, country;
                        username = ((EditText) bubbles.findViewById(R.id.editTextPersonName)).getText().toString().trim();
                        if (username.isEmpty()) username = null;
                        street = ((EditText) bubbles.findViewById(R.id.editTextStreetName)).getText().toString().trim();
                        if (street.isEmpty()) street = null;
                        city = ((EditText) bubbles.findViewById(R.id.editTextCityName)).getText().toString().trim();
                        if (city.isEmpty()) city = null;
                        country = ((Spinner) bubbles.findViewById(R.id.countrypicker2)).getSelectedItem().toString();
                        province = ((Spinner) bubbles.findViewById(R.id.statepicker2)).getSelectedItem().toString();
                        if (country.equals("Pick country")) country = null;
                        if (province.equals("N/A") || province.equals("Pick territory") || province.equals("Pick state")) {
                            province = null;
                            city = null;
                        }


                        String finalUsername = username;
                        String finalStreet = street;
                        String finalCity = city;
                        String finalProvince = province;
                        String finalCountry = country;
                        new Thread() {
                            @Override
                            public void run() {
                                List<String> keptNodes = new ArrayList<>();
                                if (finalUsername != null) {
                                    for (String ID : loadedRecords.keySet()) {
                                        if (loadedRecords.get(ID).get("user").toLowerCase(Locale.ROOT).contains(finalUsername.toLowerCase(Locale.ROOT))) {
                                            if (!userExact[0] || loadedRecords.get(ID).get("user").toLowerCase(Locale.ROOT).equals(finalUsername.toLowerCase(Locale.ROOT))) {
                                                keptNodes.add(ID);
                                            }
                                        }
                                    }
                                    cleanDB(keptNodes);
                                }
                                if (finalProvince != null) {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            findIDOfCity(finalCity, finalProvince, finalCountry);
                                        }
                                    });
                                    waitForMapData = new CountDownLatch(1);
                                    try {
                                        waitForMapData.await();
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                    for (String ID : loadedRecords.keySet()) {
                                        String rID = loadedRecords.get(ID).get("roadID");
                                        if (roads.contains((long) (Double.parseDouble(rID)))) {
                                            keptNodes.add(ID);
                                        }
                                    }
                                    cleanDB(keptNodes);
                                }
                                if (finalStreet != null) {
                                    for (String ID : loadedRecords.keySet()) {
                                        if (RegisterActivity.streetNames.get((long) Double.parseDouble(loadedRecords.get(ID).get("roadID"))).toLowerCase(Locale.ROOT).contains(finalStreet.toLowerCase(Locale.ROOT))) {
                                            if (!streetExact[0] || RegisterActivity.streetNames.get((long) Double.parseDouble(loadedRecords.get(ID).get("roadID"))).toLowerCase(Locale.ROOT).equals(finalStreet.toLowerCase(Locale.ROOT))) {
                                                keptNodes.add(ID);
                                            }
                                        }
                                    }
                                    cleanDB(keptNodes);
                                }

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        LinearLayout v = findViewById(R.id.leaderboardScollyThing);
                                        v.removeAllViews();
                                        loadPages(0, c);
                                        a[0].dismiss();
                                    }
                                });
                            }
                        }.start();
                    }
                });

            }
        });

        loadPages(0, c);
    }

    public String convertWithIteration(Map<String, ?> map) {
        StringBuilder mapAsString = new StringBuilder("{");
        for (String key : map.keySet()) {
            mapAsString.append(key + "=" + map.get(key) + ", ");
        }
        mapAsString.delete(mapAsString.length() - 2, mapAsString.length()).append("}");
        return mapAsString.toString();
    }

    void fillRecords() {
        for (int i = 0; i < 100; i++) {
            Map<String, String> record = new HashMap<>();
            record.put("firstTime", String.valueOf(System.currentTimeMillis() - 100000000));
            record.put("speedRecordTime", String.valueOf(System.currentTimeMillis()));
            record.put("speedRecord", String.valueOf((int) (Math.random() * 100d)));
            record.put("roadID", String.valueOf((int) (Math.random() * 1000d)));
            String[] names = new String[]{"Mike", "Ewan", "Rotch"};
            record.put("user", names[(int) (Math.random() * 3)]);
            loadedRecords.put(java.util.UUID.randomUUID().toString(), record);
            System.out.println("Filled in with " + record);
            recordsFilled = true;
        }
    }

    void sortByVariable(String var, Context c, int iBase) {
        try {
            String[][] idSpeed = new String[loadedRecords.size()][2];
            String[] IDs = loadedRecords.keySet().toArray(new String[0]);
            for (int i = 0; i < idSpeed.length; i++) {
                idSpeed[i][0] = IDs[i];
                idSpeed[i][1] = loadedRecords.get(IDs[i]).get(var);
            }
            if (!var.equals("user"))
                Arrays.sort(idSpeed, Comparator.comparing((String[] s) -> Double.parseDouble(s[1])));
            else Arrays.sort(idSpeed, Comparator.comparing((String[] s) -> s[1].toLowerCase(Locale.ROOT)));
            for (int i = 0; i < idSpeed.length; i++) {
                IDs[i] = idSpeed[i][0];
            }
            if (sortBackwards) Collections.reverse(Arrays.asList(IDs));
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    placePages(IDs, iBase, c);
                }
            });
        } catch (NullPointerException e) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(c, R.string.nullMessage, Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                }
            });
        }
    }

    void loadPages(int iBase, Context c) {

        LayoutInflater inflater = (LayoutInflater) c.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        layout = findViewById(R.id.leaderboardScollyThing);
        //  loadedTrials = (Map<String, Map<String, List<String>>>) RegisterActivity.savedDB.get("timeTrialPoints");
        inflater.inflate(R.layout.sorting_loading_page, layout);
        new Thread(new Runnable() {
            @Override
            public void run() {
                sortByVariable(sortMethod, c, iBase);
            }
        }).start();
    }

    void placePages(String[] recordKeys, int iBase, Context c) {
        weDidAnything = false;
        boolean placed = false;
        final ScrollView[] v = {((Activity) c).findViewById(R.id.annoyingScrollView)};
        LayoutInflater inflater = (LayoutInflater) c.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        layout.removeAllViews();
        for (int i = iBase; i < 50 + iBase && i < recordKeys.length; i++) {//String trialName : loadedTrials.keySet()) {
            if (i > 49 && !placed) {
                placed = true;
                inflater.inflate(R.layout.prev_100_records, layout);
                Button b = layout.findViewById(R.id.prev100button);
                b.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        layout.removeAllViews();
                        loadPages(iBase - 50, c);
                        v[0] = findViewById(R.id.annoyingScrollView);
                        scrollToBottom = true;
                    }
                });
            }
            View newTrialCard = inflater.inflate(R.layout.street_speed_card, layout);
            for (int index = 0; index < ((ViewGroup) newTrialCard).getChildCount(); index++) {
                View nextChild = ((ViewGroup) newTrialCard).getChildAt(index);
                for (int index1 = 0; index1 < ((ViewGroup) nextChild).getChildCount(); index1++) {
                    View nextChild2 = ((ViewGroup) nextChild).getChildAt(index1);
                    if (nextChild2 instanceof Button) {
                        if (((Button) nextChild2).getText().equals("100 MPH")) {
                            double kmh = Double.valueOf(loadedRecords.get(recordKeys[i]).get("speedRecord"));
                            if (RegisterActivity.MPH) {
                                kmh *= 0.62137119224;
                            }
                            DecimalFormat df = new DecimalFormat("#");
                            String speed = df.format(kmh);
                            ((TextView) nextChild2).setText(speed + (RegisterActivity.MPH ? " MPH" : " KMH"));
                        }
                    }
                    if (nextChild2 instanceof Button) {
                        if (((Button) nextChild2).getText().equals("a")) {
                            ((Button) nextChild2).setText(R.string.map);
                            String roadBtnLink = loadedRecords.get(recordKeys[i]).get("roadID");
                            nextChild2.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://openstreetmap.org/way/" + roadBtnLink));
                                    startActivity(browserIntent);
                                }
                            });
                        }
                    }
                    if (nextChild2 instanceof TextView) {
                        String rID = RegisterActivity.streetNames.get((long) (Double.parseDouble(loadedRecords.get(recordKeys[i]).get("roadID"))));
                        if (((TextView) nextChild2).getText().equals("Really God Awful Road, Victoria, BC😡"))
                            if (rID != null && !rID.trim().isEmpty()) {
                                ((TextView) nextChild2).setText(rID);
                            } else {
                                ((TextView) nextChild2).setText(R.string.streetNotInDB);
                            }
                    }
                    if (((TextView) nextChild2).getText().equals("First driven 1/1/70 1:00PM")) {
                        try {
                            SimpleDateFormat jdf = new SimpleDateFormat("MM/dd/YY hh:mm a");
                            String java_date = jdf.format(new Date((long) Double.parseDouble(loadedRecords.get(recordKeys[i]).get("firstTime"))));
                            ((TextView) nextChild2).setText(getString(R.string.firstDriven) + java_date);
                        } catch (Exception e) {
                            ((TextView) nextChild2).setText(R.string.failedDateLoad);
                        }

                    }
                    if (((TextView) nextChild2).getText().equals("Record set 1/1/70 1:00PM")) {
                        try {
                            SimpleDateFormat jdf = new SimpleDateFormat("MM/dd/YY hh:mm a");
                            String java_date = jdf.format(new Date((long) Double.parseDouble(loadedRecords.get(recordKeys[i]).get("firstTime"))));
                            ((TextView) nextChild2).setText(getString(R.string.recSet) + java_date);
                        } catch (Exception e) {
                            ((TextView) nextChild2).setText(R.string.failedDateLoad);
                        }
                    }
                    if (((TextView) nextChild2).getText().equals("thisIsTheMaker")) {
                        try {
                            ((TextView) nextChild2).setText("🏁 " + loadedRecords.get(recordKeys[i]).get("user"));
                            weDidAnything = true;
                        } catch (Exception e) {
                            ((TextView) nextChild2).setText(R.string.failNameLoad);
                        }
                    }
                }
            }
            if (!(i < 49 + iBase) && i + 1 < recordKeys.length) {
                inflater.inflate(R.layout.next_100_records, layout);
                Button b = layout.findViewById(R.id.next100button);
                b.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        layout.removeAllViews();
                        loadPages(iBase + 50, c);
                    }
                });
            }
        }
        if (!weDidAnything) runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(c, R.string.nullMessage, Toast.LENGTH_LONG).show();
            }
        });
        if (scrollToBottom) {
            v[0].postDelayed(new Runnable() {
                @Override
                public void run() {
                    v[0].fullScroll(View.FOCUS_DOWN);
                }
            }, 1);
            scrollToBottom = false;
        }
    }

    void findIDOfCity(String city, String province, String country) {
        RoadDiscoveryActivity c = this;
        filterBlurb.setText(R.string.findCityMsg);

        String url = "https://nominatim.openstreetmap.org/search?q=" + (city == null ? "" : city + ",+") + province + ",+" + country + "&format=json";
        StringRequest stringRequest = new StringRequest(Request.Method.POST, url, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                try {
                    String ID = response.substring(response.indexOf("\"relation\",\"osm_id\":"));
                    ID = ID.substring(ID.indexOf(":") + 1);
                    ID = ID.substring(0, ID.indexOf(","));
                    readStreets(new String[]{ID}, c, c, myQueue, filterBlurb, waitForMapData, null);
                } catch (IndexOutOfBoundsException e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(c, R.string.error, Toast.LENGTH_SHORT).show();
                        }
                    });
                }

            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                filterBlurb.setText(String.valueOf(error));
            }

        });
        stringRequest.setRetryPolicy(new DefaultRetryPolicy(999999999, 0, 0));
        myQueue.add(stringRequest);
    }
}
