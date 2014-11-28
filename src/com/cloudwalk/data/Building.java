/*
 * @(#)Person.java (part of 'Flight Club')
 * 
 * This code is covered by the GNU General Public License
 * detailed at http://www.gnu.org/copyleft/gpl.html
 *	
 * Flight Club docs located at http://www.danb.dircon.co.uk/hg/hg.htm
 * Copyright 2001-2002 Dan Burton <danb@dircon.co.uk>
 */
package com.cloudwalk.data;

import com.cloudwalk.framework3d.ModelViewer;
import com.cloudwalk.framework3d.Obj3d;

public class Building {
	public final static int NUM_POLYGONS = 5;
	final static float scale = 0.05f;

	public static void createBuilding(ModelViewer modelViewer, int a, int b, int c, float x, float y, float z, int color) {
		Obj3d o = new Obj3d(modelViewer);

		float[][] pol;
		float[][][] pols = new float[5][][];

		pol = new float[][] { { 0, 0, 0 }, { a, 0, 0 }, { a, 0, c }, { 0, 0, c } };
		pols[0] = pol;
		pol = new float[][] { { 0, b, c }, { a, b, c }, { a, b, 0 }, { 0, b, 0 } };
		pols[1] = pol;
		pol = new float[][] { { a, 0, 0 }, { a, b, 0 }, { a, b, c }, { a, 0, c } };
		pols[2] = pol;
		pol = new float[][] { { 0, 0, 0 }, { 0, 0, c }, { 0, b, c }, { 0, b, 0 } };
		pols[3] = pol;
		pol = new float[][] { { 0, 0, c }, { a, 0, c }, { a, b, c }, { 0, b, c } };
		pols[4] = pol;

		for (int k = 0; k < 5; k++) {
			for (int i = 0; i < 4; i++) {
				for (int j = 0; j < 3; j++) {
					float offset = x;
					if (j % 3 == 1)
						offset = y;
					else if (j % 3 == 2)
						offset = z;
					pols[k][i][j] = pols[k][i][j] * scale + offset;

				}
			}
		}
		for (int k = 0; k < 5; k++) {
			o.addPolygon(pols[k], color);
		}
		return;
	}

	public static void createPyramid(ModelViewer modelViewer, int a, int b, int c, float x, float y, float z, int color) {
		Obj3d o = new Obj3d(modelViewer);

		float[][] pol;
		float[][][] pols = new float[4][][];

		pol = new float[][] { { 0, 0, 0 }, { a, 0, 0 }, { a / 2f, b / 2f, c } };
		pols[0] = pol;
		pol = new float[][] { { 0, b, 0 }, { a / 2f, b / 2f, c }, { a, b, 0 } };
		pols[1] = pol;
		pol = new float[][] { { a, 0, 0 }, { a, b, 0 }, { a / 2f, b / 2f, c } };
		pols[2] = pol;
		pol = new float[][] { { 0, 0, 0 }, { a / 2f, b / 2f, c }, { 0, b, 0 } };
		pols[3] = pol;

		for (int k = 0; k < 4; k++) {
			for (int i = 0; i < 3; i++) {
				for (int j = 0; j < 3; j++) {
					float offset = x;
					if (j % 3 == 1)
						offset = y;
					else if (j % 3 == 2)
						offset = z;
					pols[k][i][j] = pols[k][i][j] * scale + offset;

				}
			}
		}
		for (int k = 0; k < 4; k++) {
			o.addPolygon(pols[k], color);
		}
		return;
	}

	public static void createHouse(ModelViewer modelViewer, int a, int b, int c, float x, float y, float z, int color) {
		createBuilding(modelViewer, a, b, c, x, y, z, color);
		createPyramid(modelViewer, a, b, c / 3, x, y, z + c * scale, color);
	}

	public static void createTree(ModelViewer modelViewer, int a, int b, int c, float x, float y, float z, int color) {
		createBuilding(modelViewer, a, b, c, x, y, z + c * scale, color);
		createBuilding(modelViewer, a / 5, b / 5, c, x + a * .4f * scale, y + b * .4f * scale, z, color);
	}
}
