package com.cloudwalk.flightclub;

import java.util.List;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

public class TasksAdapter extends BaseAdapter {
	private static String TAG = "FC " + TasksAdapter.class.getSimpleName();
	private Context mContext;
	private List<TaskDesc> tasks;

	public TasksAdapter(Context c, List<TaskDesc> list, View gridView) {
		mContext = c;
		tasks = list;
	}


	public int getCount() {
		return tasks.size();
	}

	public Object getItem(int position) {
		return null;
	}

	public long getItemId(int position) {
		return 0;
	}

	// create a new ImageView for each item referenced by the Adapter
	public View getView(int position, View convertView, ViewGroup parent) {
		if (convertView == null) {
			convertView = LayoutInflater.from(mContext).inflate(R.layout.my_grid_item, parent, false);
		}
		TextView title =		 (TextView) convertView.findViewById(R.id.title);
		TextView desc =		 (TextView) convertView.findViewById(R.id.desc);
		// textView.setTextAppearance(mContext, R.style.GridItemText);
		title.setText(tasks.get(position).title);
		desc.setText(tasks.get(position).desc);
		convertView.setTag(tasks.get(position).id);
		return convertView;
	}
}
