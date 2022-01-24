package dev.stickbit.speedleaderboard;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;

public class TimeTrialScoreView extends AppCompatActivity {

    @Override
    protected void onResume() {
        super.onResume();
        RegisterActivity.checkStarted(this);

    }

    @Override
    public void onBackPressed() {
        finish();
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_time_trial_score_view);
        TextView t = findViewById(R.id.ttrialnamebanner);
        LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        t.setText(getIntent().getStringExtra("trialName"));
        View layout = findViewById(R.id.linearLayoutTTrialScore);
        inflater.inflate(R.layout.sorting_loading_page, (ViewGroup) layout);
        new Thread(new Runnable() {
            @Override
            public void run() {
                Map<String, String> nameTime = null;
                String[][] records;
                try {
                    nameTime = (Map<String, String>) ((Map) RegisterActivity.savedDB.get("timeTrialScores")).get(t.getText());
                    records = new String[nameTime.size()][2];
                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ((ViewGroup) layout).removeAllViews();
                            Toast.makeText(layout.getContext(), R.string.nullMessage, Toast.LENGTH_LONG).show();
                        }
                    });
                    return;
                }
                String[] keys = nameTime.keySet().toArray(new String[0]);
                for (int i = 0; i < nameTime.size(); i++) {
                    records[i][0] = keys[i];
                    records[i][1] = nameTime.get(keys[i]);
                }
                Arrays.sort(records, Comparator.comparingDouble(o -> Double.parseDouble(o[1])));
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ((ViewGroup) layout).removeAllViews();
                        for (int j = 0; j < records.length; j++) {
                            View trialScoreCard = inflater.inflate(R.layout.time_trial_score_card, (ViewGroup) layout);
                            for (int i = 0; i < ((ViewGroup) trialScoreCard).getChildCount(); i++) {
                                View v = ((ViewGroup) trialScoreCard).getChildAt(i);
                                if (v instanceof ConstraintLayout) {
                                    for (int x = 0; x < ((ViewGroup) v).getChildCount(); x++) {
                                        View c = ((ViewGroup) v).getChildAt(x);
                                        if (c instanceof TextView) {
                                            if (((TextView) c).getText().equals("#0")) {
                                                ((TextView) c).setText("#" + (j + 1));
                                            }
                                            if (((TextView) c).getText().equals("Driver Name - driver time")) {
                                                int HH = (int) (Double.parseDouble(records[j][1]) / 3600);
                                                int MM = (int) ((Double.parseDouble(records[j][1]) % 3600) / 60);
                                                double SS = Double.parseDouble(records[j][1]) % 60;
                                                DecimalFormat df = new DecimalFormat("#.##");
                                                SS = Double.parseDouble(df.format(SS));
                                                ((TextView) c).setText(records[j][0] + " 🏁 " + (HH > 0 ? (HH + " h, ") : ("")) + (MM > 0 ? (MM + " m, ") : ("")) + SS + " s");
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                });

            }
        }).start();

        System.out.println("we broke it");
    }
}