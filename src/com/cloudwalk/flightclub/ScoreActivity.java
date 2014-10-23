package com.cloudwalk.flightclub;

import android.os.Bundle;

import com.google.android.gms.games.Games;
import com.google.example.games.basegameutils.BaseGameActivity;

public class ScoreActivity extends BaseGameActivity {
	int pilot_type;
	int best_time;
	boolean show_all;
	String task;
	String LEADERBOARD;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_score);

		pilot_type = getIntent().getIntExtra("pilot_type", 0);
		best_time = getIntent().getIntExtra("best_time", 10000);
		show_all = getIntent().hasExtra("show_all");
		task = getIntent().getStringExtra("task");
		if ("default".equals(task)) {
			switch (pilot_type) {
			case 0:
				LEADERBOARD = getResources().getString(R.string.leaderboard_task_1__pg);
				break;
			case 1:
				LEADERBOARD = getResources().getString(R.string.leaderboard_task_1__hg);
				break;
			case 2:
				LEADERBOARD = getResources().getString(R.string.leaderboard_task_1__sailplane);
				break;

			default:
				break;
			}
		} else if ("t001".equals(task)) {
			switch (pilot_type) {
			case 0:
				LEADERBOARD = getResources().getString(R.string.leaderboard_task_2__pg);
				break;
			case 1:
				LEADERBOARD = getResources().getString(R.string.leaderboard_task_2__hg);
				break;
			case 2:
				LEADERBOARD = getResources().getString(R.string.leaderboard_task_2__sailplane);
				break;

			default:
				break;
			}

		} else if ("t002".equals(task)) {
			switch (pilot_type) {
			case 0:
				LEADERBOARD = getResources().getString(R.string.leaderboard_task_3__pg);
				break;
			case 1:
				LEADERBOARD = getResources().getString(R.string.leaderboard_task_3__hg);
				break;
			case 2:
				LEADERBOARD = getResources().getString(R.string.leaderboard_task_3__sailplane);
				break;

			default:
				break;
			}
			
		} else if ("t003".equals(task)) {
			switch (pilot_type) {
			case 0:
				LEADERBOARD = getResources().getString(R.string.leaderboard_task_4__pg);
				break;
			case 1:
				LEADERBOARD = getResources().getString(R.string.leaderboard_task_4__hg);
				break;
			case 2:
				LEADERBOARD = getResources().getString(R.string.leaderboard_task_4__sailplane);
				break;

			default:
				break;
			}
			
		}
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
			Games.Leaderboards.submitScore(getApiClient(), LEADERBOARD, best_time);
			startActivityForResult(Games.Leaderboards.getLeaderboardIntent(getApiClient(), LEADERBOARD), 111);
		}
		finish();
	}
}
