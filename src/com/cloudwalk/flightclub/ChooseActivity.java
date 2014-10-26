package com.cloudwalk.flightclub;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.RadioGroup;

public class ChooseActivity extends Activity {
	boolean net = false;
	Menu menu;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_choose);
		if (getIntent().hasExtra("net"))
			net = true;
		findViewById(R.id.choose_continue).setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				RadioGroup group1 = (RadioGroup) findViewById(R.id.radioGlider);
				int id = group1.getCheckedRadioButtonId();
				int glider = 0;
				if (id == R.id.radio1)
					glider = 1;
				else if (id == R.id.radio2)
					glider = 2;
				RadioGroup group2 = (RadioGroup) findViewById(R.id.radioTask);
				id = group2.getCheckedRadioButtonId();
				String task = "default";
				if (id == R.id.radio11)
					task = "t001";
				else if (id == R.id.radio22)
					task = "t002";
				else if (id == R.id.radio33)
					task = "t003";
				else if (id == R.id.radio44)
					task = "default5";
				Intent intent = new Intent(ChooseActivity.this, StartFlightClub.class);
				intent.putExtra("glider", glider);
				intent.putExtra("task", task);
				if (net)
					intent.putExtra("net", true);
				startActivity(intent);
			}
		});
	}

	public boolean onCreateOptionsMenu(android.view.Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.options_menu, menu);
		this.menu = menu;
		return true;
	};

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		try {
			if (item.getItemId() == R.id.settings) {
				Intent launchPreferencesIntent = new Intent().setClass(this, Preferences.class);
				startActivity(launchPreferencesIntent);
				return true;
			} else if (item.getItemId() == R.id.leaderboards) {
				Intent launchScoresIntent = new Intent().setClass(this, ScoreActivity.class);
				launchScoresIntent.putExtra("show_all", true);
				startActivity(launchScoresIntent);
				return true;
			}
		} catch (Exception e) {
			Log.e("FC", e.toString(), e);
		}
		return true;
	}

}
