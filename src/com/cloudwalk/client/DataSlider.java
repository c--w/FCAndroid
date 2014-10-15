/*
  DataSlider.java (part of 'Flight Club')
	
  This code is covered by the GNU General Public License
  detailed at http://www.gnu.org/copyleft/gpl.html
	
  Flight Club docs located at http://www.danb.dircon.co.uk/hg/hg.htm
  Dan Burton , Nov 2001 
 */

package com.cloudwalk.client;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.Paint.Style;
import android.graphics.RectF;

import com.cloudwalk.framework3d.ModelViewer;

/**
 * This class implements a dot on a line - use for eg vario minimal design. cf.
 * Toshiba scan of Fred (update 2003 - Fred became Oscar !)
 */
public class DataSlider {
	ModelViewer app;
	int size; // length of slider in pixels
	int dotSize;
	int x0, y0; // screen coords of center point of slider
	private float v; // value to display
	int v_;// screen coord of v (v_min = 0, v_max = size)
	float v_min, v_max;
	String label = null;
	int color = Color.GRAY;
	int color2 = Color.DKGRAY;
	Paint p = new Paint();

	static final int SIZE_DEFAULT = 20; // default radius of 10
	static final int dx = 2;
	static final int dy = 10; // pixel space for label at bottom

	public DataSlider(ModelViewer theApp, float inV_min, float inV_max, int inSize, int inX0, int inY0) {
		app = theApp;
		v_min = inV_min;
		v_max = inV_max;
		size = inSize;
		x0 = inX0;
		y0 = inY0;
		init();
	}

	public DataSlider(ModelViewer theApp) {
		this(theApp, -1, 1, SIZE_DEFAULT, 50, 42);
	}

	void init() {
		/*
		 * need this ?
		 */
		setValue((v_min + v_max) / 2);
		dotSize = size/4;
		p.setColor(color);
		p.setTypeface(Typeface.DEFAULT);
		p.setTextSize(size/2);
		p.setStyle(Style.FILL);

	}

	void setValue(float inV) {
		/*
		 * clamp value and convert to 'screen' coords
		 */
		v = inV;
		if (v <= v_min)
			v = v_min;
		if (inV >= v_max)
			v = v_max;

		v = inV;
		v_ = (int) (((v - v_min) / (v_max - v_min)) * size);
	}

	public void draw(Canvas g) {

		p.setColor(color);
		g.drawLine(x0 - dx, y0 - size - dy, x0 + dx, y0 - size - dy, p);
		g.drawLine(x0 - dx, y0 - dy, x0 + dx, y0 - dy, p);
		g.drawLine(x0, y0 - dy, x0, y0 - size - dy, p);

		if (label != null) {
			// Font font = new Font("SansSerif", Font.PLAIN, 10);
			// g.setFont(font);
			//g.drawText(label, x0 - 10, y0, p);
		}

		p.setColor(color2);
		g.drawOval(new RectF(x0 - dotSize/2, y0 - v_ - dy - dotSize/2, x0 +dotSize/2, y0 - v_ - dy + dotSize/2), p);

	}
}
