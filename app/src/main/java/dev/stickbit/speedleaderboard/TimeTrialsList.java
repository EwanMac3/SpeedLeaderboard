package dev.stickbit.speedleaderboard;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;
import java.util.Map;

public class TimeTrialsList extends AppCompatActivity {
    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        RegisterActivity.checkStarted(this);

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_time_trials_list);
        LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View layout = findViewById(R.id.linearLayoutTTrialScore);
        Map<String, List<String>> loadedTrials = null;
        try {
            loadedTrials = (Map) RegisterActivity.savedDB.get("timeTrialPoints");
            for (String trialName : loadedTrials.keySet()) {
                View newTrialCard = inflater.inflate(R.layout.time_trial_card, (ViewGroup) layout);
                for (int index = 0; index < ((ViewGroup) newTrialCard).getChildCount(); index++) {
                    View nextChild = ((ViewGroup) newTrialCard).getChildAt(index);
                    for (int index1 = 0; index1 < ((ViewGroup) nextChild).getChildCount(); index1++) {
                        View nextChild2 = ((ViewGroup) nextChild).getChildAt(index1);
                        try {
                            if (((TextView) nextChild2).getText().equals("Munn Runn")) {
                                ((TextView) nextChild2).setText(String.valueOf(trialName));
                            }
                        } catch (Exception ignored) {
                        }
                        try {
                            if (((Button) nextChild2).getText().equals(getString(R.string.leadersBtn))) {
                                nextChild2.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View view) {
                                        Intent i = new Intent(view.getContext(), TimeTrialScoreView.class);
                                        i.putExtra("trialName", String.valueOf(trialName));
                                        startActivity(i);
                                    }
                                });
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        } catch (NullPointerException e) {
            Toast.makeText(this, R.string.nullMessage, Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }
}