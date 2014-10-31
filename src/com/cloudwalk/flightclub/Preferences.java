package com.cloudwalk.flightclub;

import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.preference.PreferenceActivity;

public class Preferences extends PreferenceActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Load the preferences from an XML resource
		addPreferencesFromResource(R.xml.preferences);
		String app_ver;
		try {
			app_ver = getApplicationContext().getPackageManager().getPackageInfo(this.getPackageName(), 0).versionName;
			setTitle("Flight Club - v." + app_ver);
		} catch (NameNotFoundException e1) {
		}
		
	}

}
