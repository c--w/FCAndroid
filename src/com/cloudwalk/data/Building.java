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
import com.cloudwalk.framework3d.Obj3dStatic;

public class Building {
	public final static int NUM_POLYGONS = 5;
	final static float scale = 0.05f;

	public static void createBuilding(ModelViewer modelViewer, float a, float b, float c, float x, float y, float z, int color) {
		boolean no_vbo = modelViewer.modelEnv.getPrefs().getBoolean("no_vbo", false);
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
		if (no_vbo) {
			Obj3d o = new Obj3d(modelViewer);
			for (int k = 0; k < 5; k++) {
				o.addPolygon(pols[k], color);
			}
		} else {
			for (int k = 0; k < 5; k++) {
				Obj3dStatic.addPolygon(pols[k], color);
			}
		}
		return;
	}

	public static void createPyramid(ModelViewer modelViewer, float a, float b, float c, float x, float y, float z, int color) {
		boolean no_vbo = modelViewer.modelEnv.getPrefs().getBoolean("no_vbo", false);

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
		if (no_vbo) {
			Obj3d o = new Obj3d(modelViewer);
			for (int k = 0; k < 4; k++) {
				o.addPolygon(pols[k], color);
			}
		} else {
			for (int k = 0; k < 4; k++) {
				Obj3dStatic.addPolygon(pols[k], color);
			}
		}
		return;
	}

	public static void createHouse(ModelViewer modelViewer, float a, float b, float c, float x, float y, float z, int color, int color2) {
		createBuilding(modelViewer, a, b, c, x, y, z, color);
		createPyramid(modelViewer, a, b, c / 3f, x, y, z + c * scale, color2);
	}

	public static void createPineTree(ModelViewer modelViewer, float a, float b, float c, float x, float y, float z, int color, int color2) {
		createPyramid(modelViewer, a, b, c * 1.5f, x, y, z + c / 2f * scale, color);
		createBuilding(modelViewer, a / 5f, b / 5f, c / 2f, x + a * .4f * scale, y + b * .4f * scale, z, color2);
	}

	public static void createTree(ModelViewer modelViewer, float a, float b, float c, float x, float y, float z, int color, int color2) {
		createBuilding(modelViewer, a, b, c, x, y, z + c / 1.5f * scale, color);
		createBuilding(modelViewer, a / 5f, b / 5f, c / 1.5f, x + a * .4f * scale, y + b * .4f * scale, z, color2);
	}
}
