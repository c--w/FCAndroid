/*
 * @(#)Terrain.java (part of 'Flight Club')
 * 
 * This code is covered by the GNU General Public License
 * detailed at http://www.gnu.org/copyleft/gpl.html
 *	
 * Flight Club docs located at http://www.danb.dircon.co.uk/hg/hg.htm
 * Copyright 2001-2002 Dan Burton <danb@dircon.co.uk>
 */
package com.cloudwalk.client;

import android.graphics.Color;

import com.cloudwalk.framework3d.*;

/**
 * This class implements the terrain for a site. The terrain is a N * N grid of bent triangles which covers a ground area of 50 * 50.
 */
public class Terrain extends Obj3d {
	float dx, dy;
	float x0, y0;
	static final int NUM_CELLS = 20;
	public static final float SITE_SIZE = 50f;

	public Terrain(ModelViewer modelViewer, float x0, float y0, boolean register) {
		super(modelViewer); // two triangles per cell
		this.x0 = x0;
		this.y0 = y0;
		dx = dy = SITE_SIZE / NUM_CELLS;
		// createPolygons();
	}

	public float getX0() {
		return x0;
	}

	public float getY0() {
		return y0;
	}

	/** Adds the triangles. */
	public void createPolygons(float[][] hss) {
		for (int i = 0; i < NUM_CELLS; i++) {
			for (int j = 0; j < NUM_CELLS; j++) {
				addCell(i, j, hss);
			}
		}
	}

	private void addCell(int i, int j, float[][] hss) {
		float x, y;
		x = i * dx;
		y = j * dy;
		int color = Color.rgb(255, 255, 255);
		this.addPolygon(
				new float[][] { { x, y, hss[i][j] }, { x + dx, y, hss[i + 1][j] }, { x + dx, y + dy, hss[i + 1][j + 1] }, { x, y + dy, hss[i][j + 1] } }, color);
	}

}
