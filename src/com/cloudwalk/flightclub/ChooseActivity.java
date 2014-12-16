package com.cloudwalk.flightclub;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;
import android.widget.RadioGroup;

public class ChooseActivity extends Activity {
	boolean net = false;
	Menu menu;
	GridView gridView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_choose);
		if (getIntent().hasExtra("net"))
			net = true;
		
		List<TaskDesc> tasks = new ArrayList<TaskDesc>();
		tasks.add(new TaskDesc("default", "Task 1", "D: 50km, TP: 2, CB: 1500m"));
		tasks.add(new TaskDesc("t001", "Task 2", "D: 100km, CB: 1500m"));
		tasks.add(new TaskDesc("t002", "Task 3", "D: 70km, CB: 1600m"));
		tasks.add(new TaskDesc("t003", "Task 4", "D: 120km, TP: 4, CB: 1600m"));
		tasks.add(new TaskDesc("default5", "Task 5", "150km, TP: 6, CB: 1500m"));
		tasks.add(new TaskDesc("default6", "Task 6", "Free dist., CB: 1500m"));
		tasks.add(new TaskDesc("default7", "Task 7", "D: 50km, TP: 3, CB: 1000m+-"));
		tasks.add(new TaskDesc("default8", "Task 8", "D: 160km, TP: 1, CB: 1800m+-"));
		gridView = (GridView) findViewById(R.id.gridview);
		gridView.setAdapter(new TasksAdapter(ChooseActivity.this, tasks, gridView));
		gridView.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View vv, int position, long id) {
				String task = (String) vv.getTag();
				RadioGroup group1 = (RadioGroup) findViewById(R.id.radioGlider);
				int gid = group1.getCheckedRadioButtonId();
				int glider = 0;
				if (gid == R.id.radio1)
					glider = 1;
				else if (gid == R.id.radio2)
					glider = 2;
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
				Intent launchPreferencesIntent = new Intent(this, Preferences.class);
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
