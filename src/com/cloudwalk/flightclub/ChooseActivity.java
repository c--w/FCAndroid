package com.cloudwalk.flightclub;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.cloudwalk.server.Client;

public class ChooseActivity extends Activity {
	boolean net = false;
	boolean net_online = false;
	Menu menu;
	GridView gridView;
	List<TaskDesc> tasks;
	String[] gliders = { "PG", "HG", "SP", "Any" };
	SharedPreferences prefs;

	private TaskDesc findTask(String id) {
		for (TaskDesc taskDesc : tasks) {
			if (taskDesc.id.equals(id))
				return taskDesc;
		}
		TaskDesc taskDesc = new TaskDesc("0", "Any", "");
		return taskDesc;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_choose);
		try {
			prefs = PreferenceManager.getDefaultSharedPreferences(this);

			if (getIntent().hasExtra("net"))
				net = true;
			if (getIntent().hasExtra("net_online"))
				net_online = true;

			tasks = new ArrayList<TaskDesc>();
			tasks.add(new TaskDesc("default", "Task 1", "D: 50km, TP: 2, CB: 1500m"));
			tasks.add(new TaskDesc("t001", "Task 2", "D: 100km, CB: 1500m"));
			tasks.add(new TaskDesc("t002", "Task 3", "D: 70km, CB: 1600m"));
			tasks.add(new TaskDesc("t003", "Task 4", "D: 120km, TP: 4, CB: 1600m"));
			tasks.add(new TaskDesc("default5", "Task 5", "150km, TP: 6, CB: 1500m"));
			tasks.add(new TaskDesc("default6", "Task 6", "Free dist., CB: 1500m"));
			tasks.add(new TaskDesc("default7", "Task 7", "D: 160km, TP: 3, CB: 1200m+-"));
			tasks.add(new TaskDesc("default8", "Task 8", "D: 160km, TP: 1, CB: 1800m+-"));
			tasks.add(new TaskDesc("default9", "Task 9", "D: 50km, TP: 2, CB: 1500m+-"));
			tasks.add(new TaskDesc("default10", "Task 10", "D: 80km, TP: 3, CB: 2000m+-"));
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
					if (net_online)
						intent.putExtra("net_online", true);
					startActivity(intent);
				}
			});

			if (net_online) {
				String info = "\n";
				JSONObject rooms = new JSONObject(Client.send("ROOMS"));
				for (Iterator<String> iterator = rooms.keys(); iterator.hasNext();) {
					String key = (String) iterator.next();
					int type = Integer.parseInt(key.substring(0, 1));
					String task = key.substring(1);
					TaskDesc taskDesc = findTask(task);
					info += taskDesc.title + ", Glider: " + gliders[type] + ", Players: " + rooms.getInt(key) + "\n";
				}
				if (info.length() > 5)
					Tools.showInfoDialog("Active Online Tasks", info, this);
				else {
					AlertDialog.Builder builder = new AlertDialog.Builder(this);

					builder.setTitle("No active task");
					builder.setMessage("If you want to wait for someone to join the online game choose 'Wait'. \nYou can leave the app and return to it when you receive notification that other players joined. \nRight now waiting time is "+prefs.getString("wait_online", "10")+"min. (change it in preferences) ");

					builder.setPositiveButton("Wait", new DialogInterface.OnClickListener() {

						public void onClick(DialogInterface dialog, int which) {
							Intent intent = new Intent(getApplicationContext(), WaitService.class);
							intent.putExtra("net_online", true);
							prefs.edit().putBoolean("wait_service_running", true).commit();
							startService(intent);
							intent = new Intent(Intent.ACTION_MAIN);
							intent.addCategory(Intent.CATEGORY_HOME);
							intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
							startActivity(intent);
							Toast.makeText(getApplicationContext(),
									"Waiting for online players in background for next " + prefs.getString("wait_online", "10") + " minutes", Toast.LENGTH_LONG)
									.show();
						}

					}).setNegativeButton("Cancel", new OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
						}
					}).show();

				}
			}

			prefs.edit().putBoolean("wait_service_running", false).commit();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

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
