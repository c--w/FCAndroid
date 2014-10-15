package com.cloudwalk.task;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

public class Link {
	Point a, b;

	public Link(Point a, Point b) {
		this.a = a;
		this.b = b;
	}

	public void draw(Canvas g, Paint paint) {

		g.drawLine((int) a.x, (int) a.y, (int) b.x, (int) b.y, paint);
	}

	public void highLight(Canvas g, Paint paint) {
		int mx, my;
		mx = (int) (a.x + b.x) / 2;
		my = (int) (a.y + b.y) / 2;
		g.drawOval(new RectF(mx - 5, my - 5, 10, 10), paint);
	}

}
