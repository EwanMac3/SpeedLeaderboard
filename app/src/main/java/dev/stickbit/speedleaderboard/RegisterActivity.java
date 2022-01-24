package dev.stickbit.speedleaderboard;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.android.volley.*;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.gson.Gson;
import org.jetbrains.annotations.NotNull;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;

import static android.content.ContentValues.TAG;

public class RegisterActivity extends AppCompatActivity {
    public static GoogleSignInAccount account;
    public static FirebaseAuth mAuth;
    public static FirebaseFirestore mDatabase;
    public static String userName;
    public static String groupName;
    public static Map<String, Object> savedDB;
    public static Map<Long, String> streetNames;
    public static boolean MPH;
    static boolean goToMenu;
    static Activity regAct;
    static boolean loadToRoads;
    static boolean startedProperly = false;
    GoogleSignInClient mGoogleSignInClient;
    boolean pendReload = true;
    Context me;
    List checkedAccounts;
    List<Long> streetNamesToCache;
    RequestQueue queue;
    CountDownLatch waitForFinish;

    static void checkStarted(Context c) {
        if (!startedProperly) {
            ((Activity) c).finish();
            c.startActivity(new Intent(c, RegisterActivity.class));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (goToMenu) startActivity(new Intent(me, MenuActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //reset static stuff
        DriveModeActivity.mainAct = null;
        account = null;
        mAuth = null;
        mDatabase = null;
        userName = null;
        groupName = null;
        savedDB = null;
        streetNames = null;
        regAct = this;
        goToMenu = false;
        RoadDiscoveryActivity.loadedRecords = null;
        RoadDiscoveryActivity.recordsFilled = false;
        RoadDiscoveryActivity.backupDb = null;
        RoadDiscoveryActivity.sortSet = false;
        RoadDiscoveryActivity.sortMethod = null;
        RoadDiscoveryActivity.sortBackwards = false;
        startedProperly = true;
        //
        if (DriveModeActivity.serviceI != null) {
            stopService(DriveModeActivity.serviceI);
            DriveModeActivity.serviceI = null;
        }
        streetNamesToCache = new LinkedList<>();
        streetNames = new HashMap<>();
        setContentView(R.layout.activity_register);
        Button b = findViewById(R.id.testButton);
        b.setVisibility(View.GONE);
        b.setText("Login!");
        b.setEnabled(false);
        mDatabase = null;
        queue = Volley.newRequestQueue(this);
        account = GoogleSignIn.getLastSignedInAccount(this);
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestEmail().requestIdToken(getString(R.string.apiCode)).build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
        signIn();
        mAuth = FirebaseAuth.getInstance();
        mAuth.addAuthStateListener(new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull @NotNull FirebaseAuth firebaseAuth) {
                FirebaseUser currentUser = mAuth.getCurrentUser();
                if (currentUser != null) {
                    waitForDb(b);
                }
            }
        });

        SharedPreferences settings = getSharedPreferences("settings", MODE_PRIVATE);
        MPH = settings.getBoolean("MPH", true);


        me = this;
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                b.setEnabled(false);
                b.setText("Reading...");
            }
        });
        findViewById(R.id.registerButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                view.setEnabled(false);
                String userName = String.valueOf(((EditText) findViewById(R.id.usernameInput)).getText());
                userName = userName.trim();
                String groupName = String.valueOf(((EditText) findViewById(R.id.groupNameInput)).getText());
                groupName = groupName.trim();
                if (userName.isEmpty() || groupName.isEmpty()) {
                    Toast.makeText(me, R.string.musntBeEmpty, Toast.LENGTH_SHORT).show();
                    view.setEnabled(true);
                    return;
                }
                Toast.makeText(me, R.string.signUpInProg, Toast.LENGTH_SHORT).show();
                Map<String, String> regData = new HashMap<>();
                regData.put("username", userName);
                regData.put("groupname", groupName);
                String finalUserName = userName;
                String finalGroupName = groupName;
                mDatabase.collection("groups").get().addOnCompleteListener(new OnCompleteListener<com.google.firebase.firestore.QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull @NotNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            checkedAccounts = task.getResult().getDocuments();

                            for (Object key : checkedAccounts) {
                                Map map = ((DocumentSnapshot) key).getData();
                                String checkusername = String.valueOf(map.get("username"));
                                String checkgroupname = String.valueOf(map.get("groupname"));
                                //  Toast.makeText(me, checkusername + checkgroupname, Toast.LENGTH_LONG).show();
                                if (checkusername.toLowerCase(Locale.ROOT).equals(finalUserName.toLowerCase(Locale.ROOT)) && checkgroupname.equals(finalGroupName)) {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(me, R.string.nameInUse, Toast.LENGTH_SHORT).show();
                                            findViewById(R.id.registerButton).setEnabled(true);
                                        }
                                    });
                                    return;
                                }
                            }
                            mDatabase.collection("groups").document(mAuth.getUid()).set(regData).addOnSuccessListener(new OnSuccessListener<Void>() {
                                @Override
                                public void onSuccess(Void aVoid) {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(me, R.string.regSuccess, Toast.LENGTH_SHORT).show();
                                            reload();
                                        }
                                    });

                                }
                            }).addOnFailureListener(new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception e) {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Log.w(TAG, "Error writing document", e);
                                            Toast.makeText(me, R.string.regFail, Toast.LENGTH_SHORT).show();
                                            findViewById(R.id.registerButton).setEnabled(true);
                                        }
                                    });
                                }
                            });


                        } else {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(me, R.string.regFail, Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    }
                });
            }
        });
    }

    private void signIn() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, 897458798);
    }

    void waitForDb(Button b) {
        mDatabase = FirebaseFirestore.getInstance();
        FirebaseFirestoreSettings set = new FirebaseFirestoreSettings.Builder().setCacheSizeBytes(1048576).setPersistenceEnabled(false).setSslEnabled(true).build();
        mDatabase.setFirestoreSettings(set);
        b.setEnabled(true);
        b.setText("Check DB");
        pendReload = false;
        checkDb();
    }

    void updateStreet() {
        String url = "https://overpass-api.de/api/interpreter";
        StringRequest stringRequest = new StringRequest(Request.Method.POST, url, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                new Thread() {
                    @Override
                    public void run() {
                        Map output = (Map) new Gson().fromJson(response, Object.class);
                        List<Map> eachTags = (List) output.get("elements");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                ((TextView) findViewById(R.id.loginStatus)).setText(R.string.mapsImporting);
                            }
                        });
                        for (Map m : eachTags) {
                            Map tag = (Map) m.get("tags");
                            if (tag.containsKey("name"))
                                streetNames.put((long) Double.parseDouble(String.valueOf(m.get("id"))), String.valueOf(tag.get("name")));
                            else if(tag.containsKey("ref"))
                                streetNames.put((long) Double.parseDouble(String.valueOf(m.get("id"))), tag.get("ref")+" (No name)");
                            else
                                streetNames.put((long) Double.parseDouble(String.valueOf(m.get("id"))), "(Unnamed Road)");

                        }
                        waitForFinish.countDown();
                    }
                }.start();
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
            }

        }) {
            @Override
            public byte[] getBody() throws AuthFailureError {
                String msg = "[out:json];\n";
                for (long ID : streetNamesToCache) {
                    msg += "way(" + ID + ");\n" + "out;";
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

    void downloadDB() {
        ((TextView) findViewById(R.id.loginStatus)).setText(R.string.databaseDownloading);
        mDatabase.collection("users").document(groupName).get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull @NotNull Task<DocumentSnapshot> task) {
                if (task.isSuccessful()) {
                    savedDB = task.getResult().getData();
                    ((TextView) findViewById(R.id.loginStatus)).setText(R.string.mapsDownloading);
                    new Thread() {
                        @Override
                        public void run() {
                            if (savedDB != null) {
                                Object roadRecords = savedDB.get("records");
                                RoadDiscoveryActivity.loadedRecords = new HashMap<>();
                                for (Object record : ((Map) roadRecords).keySet()) {
                                    streetNamesToCache.add((long) Double.parseDouble(((Map<String, Map<String, String>>) roadRecords).get(record).get("roadID")));
                                    Map<String, String> data = new HashMap<>();
                                    data.put("roadID", ((Map<String, Map<String, String>>) roadRecords).get(record).get("roadID"));
                                    data.put("firstTime", ((Map<String, Map<String, String>>) roadRecords).get(record).get("firstTime"));
                                    data.put("speedRecordTime", ((Map<String, Map<String, String>>) roadRecords).get(record).get("speedRecordTime"));
                                    data.put("speedRecord", ((Map<String, Map<String, String>>) roadRecords).get(record).get("speedRecord"));
                                    data.put("user", ((Map<String, Map<String, String>>) roadRecords).get(record).get("user"));
                                    RoadDiscoveryActivity.loadedRecords.put(record.toString(), data);
                                }
                                //     String test = "27050428andAlso469851170andAlso117597990andAlso469851172andAlso1010793485andAlso46146297andAlso46146298andAlso46146299andAlso46146300andAlso46146301andAlso46146302andAlso46146303andAlso27050460andAlso27050458andAlso27050452andAlso27050446andAlso41673257andAlso41673256andAlso41673262andAlso41673261andAlso41673260andAlso552313011andAlso27050439andAlso41673254andAlso552313014andAlso552313013andAlso704407970andAlso552313012andAlso704407971andAlso27050493andAlso27050484andAlso27050487andAlso27050468andAlso27050470andAlso27050465andAlso323384263andAlso323384260andAlso323384258andAlso323384259andAlso323384257andAlso323384264andAlso1011170507andAlso25477395andAlso476077306andAlso4964580andAlso4964490andAlso25821518andAlso25821516andAlso25821517andAlso1010138365andAlso1010138364andAlso1010138366andAlso25821509andAlso25821531andAlso25821528andAlso25821532andAlso25821533andAlso25821526andAlso25821547andAlso476019902andAlso1013914766andAlso476019900andAlso25821550andAlso25821548andAlso25821549andAlso833556807andAlso25821539andAlso476019894andAlso25821536andAlso476019891andAlso25821543andAlso25821540andAlso315249210andAlso315249211andAlso315249209andAlso315249212andAlso640173874andAlso476101963andAlso476101964andAlso476101966andAlso41657138andAlso1010376041andAlso137283963andAlso1013898549andAlso41730832andAlso132236957andAlso132236956andAlso388945583andAlso80676166andAlso147335495andAlso388945594andAlso388945593andAlso388945591andAlso388945588andAlso388945586andAlso388945587andAlso388945584andAlso388945585andAlso711584719andAlso1010826632andAlso1010826631andAlso711584720andAlso46146304andAlso46146305andAlso46146306andAlso46146307andAlso60949089andAlso46146308andAlso46146309andAlso46146310andAlso312144762andAlso312144760andAlso312144761andAlso312144767andAlso312144764andAlso312144765andAlso25478021andAlso25478023andAlso276148849andAlso25478017andAlso276017781andAlso276148851andAlso25478019andAlso25478031andAlso25478025andAlso276017790andAlso276017789andAlso25478027andAlso25478038andAlso1009499696andAlso25478043andAlso380679312andAlso25478048andAlso942447113andAlso942447114andAlso942447108andAlso942447110andAlso942447105andAlso942447107andAlso380679307andAlso380679310andAlso380679311andAlso881817295andAlso486358712andAlso444340276andAlso444340277andAlso444340272andAlso444340273andAlso444340274andAlso444340275andAlso53674347andAlso53674349andAlso135228011andAlso135228010andAlso135228009andAlso135228008andAlso335656579andAlso1011031744andAlso4489936andAlso4489937andAlso70305445andAlso75024096andAlso25944849andAlso75024098andAlso75024099andAlso75024100andAlso75024088andAlso75024089andAlso75024091andAlso75024092andAlso603111766andAlso75024094andAlso75024080andAlso75024082andAlso75024083andAlso75024085andAlso75024087andAlso75024073andAlso75024077andAlso75024078andAlso75024064andAlso75024066andAlso75024068andAlso75024070andAlso75024071andAlso75024056andAlso75024057andAlso75024058andAlso75024061andAlso75024062andAlso75024063andAlso75024048andAlso75024049andAlso75024050andAlso75024052andAlso75024045andAlso75024046andAlso25822051andAlso25478005andAlso25478008andAlso5055309andAlso154004013andAlso1014472567andAlso5055308andAlso154004012andAlso5055310andAlso5055307andAlso5055306andAlso1014472571andAlso1014472570andAlso1011040076andAlso1014472568andAlso154004025andAlso154004027andAlso154004021andAlso154004016andAlso154004019andAlso154003980andAlso154003982andAlso154003973andAlso41656658andAlso41656657andAlso154003975andAlso41656651andAlso41656650andAlso41656649andAlso41656655andAlso154003993andAlso41656654andAlso41656653andAlso154003995andAlso41656652andAlso154003985andAlso41656646andAlso466270346andAlso154004079andAlso466270344andAlso154004073andAlso466270350andAlso466270348andAlso154004074andAlso154004093andAlso466270362andAlso466270361andAlso154004089andAlso466270366andAlso154004088andAlso466270367andAlso466270364andAlso154004085andAlso466270354andAlso612075298andAlso466270352andAlso45842912andAlso132965552andAlso154004081andAlso154004080andAlso466270359andAlso154004083andAlso466270356andAlso466270357";
                                //     String[] IDs = test.split("andAlso");
                                //     for (String s : IDs) {
                                //          streetNamesToCache.add(Long.parseLong(s));
                                //       }
                            }
                            waitForFinish = new CountDownLatch(1);
                            updateStreet();
                            try {
                                waitForFinish.await();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    ((TextView) findViewById(R.id.loginStatus)).setText(streetNames.size() == 1 ? "1 " + getString(R.string.readyStCount1) : streetNames.size() + " " + getString(R.string.readyStCount));
                                    goToMenu = true;
                                    finish();
                                    startActivity(new Intent(me, MenuActivity.class));
                                }
                            });
                        }
                    }.start();
                } else {
                    ((TextView) findViewById(R.id.loginStatus)).setText(R.string.dbReadFailBanner);
                }
            }
        });
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 897458798) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            handleSignInResult(task);
        }
    }

    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            account = completedTask.getResult(ApiException.class);
            //     Toast.makeText(this, "Logged in...", Toast.LENGTH_SHORT).show();
            firebaseAuthWithGoogle(account.getIdToken());
        } catch (ApiException e) {
            e.printStackTrace();
            Toast.makeText(this, R.string.googleFail, Toast.LENGTH_SHORT).show();
            reload();
        }
    }

    @Override
    public void onBackPressed() {
        if (((TextView) findViewById(R.id.loginStatus)).getText().equals(getText(R.string.registerMsg))) {
            finish();
        }
    }

    void checkDb() {
        mDatabase.collection("groups").document(mAuth.getUid()).get().addOnCompleteListener(new OnCompleteListener() {

            @Override
            public void onComplete(@NonNull @NotNull Task task) {
                if (task.isSuccessful()) {
                    System.out.println("this was a breakpoint");
                    Map myUser = ((DocumentSnapshot) task.getResult()).getData();
                    if (myUser == null) {
                        ((TextView) findViewById(R.id.loginStatus)).setText(R.string.registerMsg);
                        findViewById(R.id.usernameInput).setVisibility(View.VISIBLE);
                        findViewById(R.id.groupNameInput).setVisibility(View.VISIBLE);
                        findViewById(R.id.caseSensWarning).setVisibility(View.VISIBLE);
                        findViewById(R.id.registerButton).setVisibility(View.VISIBLE);
                        return;
                    }

                    String gName = String.valueOf(myUser.get("groupname"));
                    String uName = String.valueOf(myUser.get("username"));
                    userName = uName;
                    groupName = gName;
                    System.out.println("Logged in! :)");
                    //  Toast.makeText(me, getString(R.string.loginmessagelead) + " " + userName + " " + getString(R.string.loginmessageFrom) + " " + gName, Toast.LENGTH_SHORT).show();
                    //    finish();
                    //  startActivity(new Intent(me, MenuActivity.class));
                    downloadDB();
                }
            }
        });


      /*  mDatabase.collection("users").get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
            @Override
            public void onComplete(@NonNull @NotNull Task<QuerySnapshot> task) {
                if (task.isSuccessful()) {
                    for (QueryDocumentSnapshot document : task.getResult()) {
                        Map users = document.getData();
                        System.out.println("damn daniel");
                    }
                } else {
                    Log.w(TAG, "Error getting documents.", task.getException());
                    Toast.makeText(me, "Failed because " + task.getException(), Toast.LENGTH_LONG).show();
                }
            }
        });*/
    }

    private void firebaseAuthWithGoogle(String idToken) {
        // Toast.makeText(me, idToken + "=token", Toast.LENGTH_LONG).show();
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential).addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
                if (task.isSuccessful()) {
                    // Sign in success, update UI with the signed-in user's information
                    //   Log.d(TAG, "signInWithCredential:success");
                    FirebaseUser user = mAuth.getCurrentUser();
                } else {
                    // If sign in fails, display a message to the user.
                    Log.w(TAG, "signInWithCredential:failure", task.getException());
                }
            }
        });
    }

    void reload() {
        finish();
        overridePendingTransition(0, 0);
        startActivity(getIntent());
        overridePendingTransition(0, 0);
    }

}