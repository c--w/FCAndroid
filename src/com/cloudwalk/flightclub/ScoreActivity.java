package com.cloudwalk.flightclub;

import android.os.Bundle;

import com.google.android.gms.games.Games;
import com.google.example.games.basegameutils.BaseGameActivity;

public class ScoreActivity extends BaseGameActivity {
	String[] TASK1_LEADERBOARD_IDS = { "CgkIyP69640HEAIQCw", "CgkIyP69640HEAIQAg", "CgkIyP69640HEAIQCg" };
	String[] TASK2_LEADERBOARD_IDS = { "CgkIyP69640HEAIQDA", "CgkIyP69640HEAIQDQ", "CgkIyP69640HEAIQDg" };
	String[] TASK3_LEADERBOARD_IDS = { "CgkIyP69640HEAIQDw", "CgkIyP69640HEAIQEA", "CgkIyP69640HEAIQEQ" };
	int pilot_type;
	int best_time;
	boolean show_all;
	String task;
	String[] LEADERBOARD;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_score);

		pilot_type = getIntent().getIntExtra("pilot_type", 0);
		best_time = getIntent().getIntExtra("best_time", 10000);
		show_all = getIntent().hasExtra("show_all");
		task = getIntent().getStringExtra("task");
		if ("default".equals(task))
			LEADERBOARD = TASK1_LEADERBOARD_IDS;
		else if ("t001".equals(task))
			LEADERBOARD = TASK2_LEADERBOARD_IDS;
		else if ("t002".equals(task))
			LEADERBOARD = TASK3_LEADERBOARD_IDS;
	}

	@Override
	public void onSignInFailed() {
		// TODO Auto-generated method stub

	}

	@Override
	public void onSignInSucceeded() {
		if (show_all) {
			startActivityForResult(Games.Leaderboards.getAllLeaderboardsIntent(getApiClient()), 111);
		} else {
			Games.Leaderboards.submitScore(getApiClient(), LEADERBOARD[pilot_type], best_time);
			startActivityForResult(Games.Leaderboards.getLeaderboardIntent(getApiClient(), LEADERBOARD[pilot_type]), 111);
		}
		finish();
	}
}
