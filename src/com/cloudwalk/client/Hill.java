package com.cloudwalk.client;

/*
 Hill.java (part of 'Flight Club')

 This code is covered by the GNU General Public License
 detailed at http://www.gnu.org/copyleft/gpl.html

 Flight Club docs located at http://www.danb.dircon.co.uk/hg/hg.htm
 Dan Burton , Nov 2001 
 */

import java.io.IOException;
import java.io.StreamTokenizer;

import android.graphics.Color;
import android.util.Log;

import com.cloudwalk.framework3d.CameraSubject;
import com.cloudwalk.framework3d.Obj3d;

/*
 a spine running // to x axis or y axis (orientation 0 or 1)
 has a circuit for ridge soaring

 build surface from maths functions...
 - taylor approx's of sin wave (cubic polynomial)
 */

class Hill implements LiftSource, CameraSubject {
	static int nextID = 0;
	int myID;
	float x0, y0; // spine start point
	int orientation = 0;
	int spineLength;
	float phase;
	float h0;
	int face;
	Task app;
	Obj3d obj3d;
	int color = Color.rgb(240, 250, 240);
	float tileWidth;// make smaller to increase curvy resolution
	int[] zorderedTiles;
	boolean inForeGround;
	float maxH = 0;

	static final int OR_X = 0;
	static final int OR_Y = 1;
	static final int FACE_SPIKEY = 0; // width 1 - spiky
	static final int FACE_CURVY = 1; // width 2 - curvy

	private Node[] myNodes = new Node[MAX_NODES];
	private static final int MAX_NODES = 3; // register with upto 3 overlapping
	private int numNodes = 0; // registered with n nodes
	Circuit circuit;

	public Hill(Task theApp, StreamTokenizer st) {
		app = theApp;
		myID = nextID++; // unique id (for debugging)
		try {
			st.nextToken();
			x0 = (float) st.nval;
			st.nextToken();
			y0 = (float) st.nval;
			st.nextToken();
			orientation = (int) st.nval;
			st.nextToken();
			spineLength = (int) st.nval;
			st.nextToken();
			phase = (float) st.nval;
			st.nextToken();
			h0 = (float) st.nval;
			st.nextToken();
			face = (int) st.nval;
			st.nextToken();
			tileWidth = (float) st.nval;
			st.nextToken();
			inForeGround = true;
			int numSlices = Math.round((spineLength + 2) / tileWidth);
			int numTiles;

			float frontFace;
			if (face == FACE_SPIKEY)
				frontFace = 1;
			else
				frontFace = 2;
			numTiles = 2 * numSlices * (2 * Math.round(frontFace / tileWidth));
			Log.i("FC Hill", "numTiles:" + numTiles);
			obj3d = new Obj3d(theApp.xcModelViewer);
			tileHill();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public Hill(Task theApp, float inX, float inY, int inOr, int inSpineLength, float inPhase, float inH0, int inFace, float inTileWidth) {
		app = theApp;
		x0 = inX;
		y0 = inY;
		orientation = inOr;
		spineLength = inSpineLength;
		phase = inPhase;
		h0 = inH0;
		face = inFace;

		tileWidth = inTileWidth;
		;

		inForeGround = true;
		int numSlices = Math.round((spineLength + 2) / tileWidth);
		int numTiles;

		float frontFace;
		if (face == FACE_SPIKEY)
			frontFace = 1;
		else
			frontFace = 2;
		numTiles = 2 * (numSlices * (2 * Math.round(frontFace / tileWidth)));
		obj3d = new Obj3d(theApp.xcModelViewer);
		tileHill();
		// registerWithNodes(true);
	}

	public Hill(Task theApp, float inX, float inY) {
		// default hill (curvy spine, curvy face)
		this(theApp, inX, inY, OR_X, 4, 1, (float) 0.5, FACE_CURVY, 0.5f);
	}

	void tileHill() {
		/*
		 * run over hill adding tiles
		 */
		int numSlices = Math.round((spineLength + 2) / tileWidth);
		int numTiles;

		float frontFace;
		if (face == FACE_SPIKEY)
			frontFace = 1;
		else
			frontFace = 2;
		numTiles = 2 * numSlices * (2 * Math.round(frontFace / tileWidth));
		zorderedTiles = new int[numTiles];

		for (float i = 0; i < spineLength + 2; i += tileWidth) {
			for (float j = -frontFace + tileWidth; j <= frontFace; j += tileWidth) {
				addTile(i, j);
			}
		}

		// if (orientation == OR_Y)
		// obj3d.reverse();
	}

	void addTile(float i, float j) {
		/*
		 * add a tile at x0 +i, y0 + j if OR_X else swap i and j
		 */
		// Log.i("FC Hill", "i j:" + i + " " + j);
		float x1, x2, y1, y2;
		float[][] corners = new float[4][3];

		if (orientation == OR_X) {
			x1 = x0 + i;
			x2 = x1 + tileWidth;
			y1 = y0 - j; // back face has j=-1 and y > y0
			y2 = y1 + tileWidth;
		} else {
			y1 = y0 + i;
			y2 = y1 + tileWidth;
			x1 = x0 + j - tileWidth; // back face has j=-1 and x < x0
			x2 = x1 + tileWidth;
		}

		// TODO make getZ transform to i/j coords
		if (orientation == OR_X) {
			corners[0] = new float[] { x1, y1, getZ(i, j) };
			corners[1] = new float[] { x2, y1, getZ(i + tileWidth, j) };
			corners[2] = new float[] { x2, y2, getZ(i + tileWidth, j - tileWidth) };
			corners[3] = new float[] { x1, y2, getZ(i, j - tileWidth) };
		} else {
			corners[0] = new float[] { x1, y1, getZ(i, j - tileWidth) };
			corners[1] = new float[] { x2, y1, getZ(i, j) };
			corners[2] = new float[] { x2, y2, getZ(i + tileWidth, j) };
			corners[3] = new float[] { x1, y2, getZ(i + tileWidth, j - tileWidth) };
		}
		//int pol_color = getTileColor(corners);
		for (float[] corner : corners) {
			int cdiff = (int) (corner[2] * 100); 
			int color = Color.rgb(242-cdiff, 255-cdiff, 242-cdiff);
			obj3d.addPoint(corner[0], corner[1], corner[2], color);
		}
		obj3d.addPolygon(corners, 0); 
		// object3d.addTile(corners, color, false, false);
	}

	int getTileColor(float[][] corners) {
		float minz = Math.abs(corners[0][2]);
		for (int i = 1; i < corners.length; i++) {
			if (Math.abs(corners[i][2]) < minz)
				minz = Math.abs(corners[i][2]);
		}
		int color_corr = (int) (minz * 50+30);
		return Color.rgb(254 - color_corr, (int) 254, 254 - color_corr);
	}

	Circuit getCircuit() {
		/*
		 * soaring circuit - treat x0,y0 as origin
		 */
		if (circuit != null)
			return circuit;
		circuit = new Circuit(2);

		float frontFace;
		if (face == FACE_CURVY)
			frontFace = 2;
		else
			frontFace = 1;

		if (orientation == OR_X) {
			circuit.add(new float[] { x0 + 1, y0, 0 });
			circuit.add(new float[] { x0 + 1 + spineLength, y0, 0 });
			circuit.fallLine = new float[] { 0, 0, 0 };
		} else {
			circuit.add(new float[] { x0, y0 + 1, 0 });
			circuit.add(new float[] { x0, y0 + 1 + spineLength, 0 });
			// override the default fall line
			circuit.fallLine = new float[] { 0, 0, 0 };
		}
		return circuit;
	}

	public float[] getEye() {
		if (orientation == OR_X) {
			return new float[] { x0 + 2 + spineLength, y0 - 2 - spineLength, (float) 0.8 };
		} else {
			return new float[] { x0 + 2 + spineLength, y0, (float) 0.8 };
		}
	}

	public float[] getFocus() {
		if (orientation == OR_X) {
			return new float[] { x0 + (2 + spineLength) / 2, y0, h0 / 2 };
		} else {
			return new float[] { x0, y0 + (2 + spineLength) / 2, h0 / 2 };
		}
	}

	public boolean contains(float[] p) {

		float frontFace;
		if (face == FACE_CURVY)
			frontFace = 2;
		else
			frontFace = 1;

		if (orientation == OR_X) {
			return (p[1] >= y0 - frontFace && p[1] <= y0 + frontFace && p[0] > x0 && p[0] < x0 + 2 + spineLength);
		} else {
			return (p[0] <= x0 + frontFace && p[0] >= x0 - frontFace && p[1] > y0 && p[1] < y0 + 2 + spineLength);
		}
	}

	float get01Value(float i, float j) {
		float a = (float) Math.sqrt((x0 + 1 + Math.abs(i)) / (y0 + 1 + Math.abs(j)));
		return (float) ((a * 1000) - Math.floor(a * 1000));
	}

	private float getZ(float i, float j) {
		/*
		 * return h at point i along spine and j away from spine
		 * 
		 * slice perp to spine gives f1... f1 = 1+j, j < 0 backface f1 = (1-j) * (1-j), j > 0 and spiky f1 = sin , j > 0 and curvy then, scale f1 by f2, h at
		 * this point on spine
		 */

		float frontFace;
		if (face == FACE_CURVY)
			frontFace = 2;
		else
			frontFace = 1;
		float f1 = 1; // tmp
		float f2 = spineHeight(i);

		if (face == FACE_CURVY) {
			f1 = sin(2 - Math.abs(j));
		} else {
			f1 = (frontFace - j) * (frontFace - j);
		}

		float h = f1 * f2;
		h = Math.abs(h);
		if (h < 0.001) {
			h = 0;
		} else {
			h += 0.1 * get01Value(i, j);
		}
		if (h > maxH)
			maxH = h;
		return h;
	}

	public float getHeight(float x, float y) {
		/*
		 * convert to local coords then call getZ
		 */
		float i, j;
		if (orientation == OR_X) {
			i = x - x0;
			j = y0 - y;
		} else {
			i = y - y0;
			j = x - x0;
		}
		return getZ(i, j);
	}

	float spineHeight(float i) {
		/*
		 * spine h a distance i along it
		 */
		if (i < 0 || i > spineLength + 2) {
			return 0;
		}

		if (i <= 1) {
			return i * i * h0;
		}

		if (i > 1 + spineLength) {
			float ii = spineLength + 2 - i;

			// beware - recursion would be infinite if
			// '>' changed to '>=' above
			float h1 = spineHeight(1 + spineLength);
			return ii * ii * h1;
		}

		return h0 + sin(i - 1 + phase) - sin(phase);
	}

	float sin(float x) {
		/*
		 * cubic approx to a sin wave with wave length 4, going between 0 and 1
		 * 
		 * 24/10 try halving amplitude
		 */
		while (x >= 4) {
			x -= 4;
		}
		while (x < 0) {
			x += 4;
		}

		float xx;
		if (x <= 2) {
			xx = x / 2;
		} else {
			xx = 2 - x / 2;
		}
		return 3 * xx * xx - 2 * xx * xx * xx;
	}

	public float getLift(float[] p) {
		/*
		 * lift twice sink rate close to hill, falling to zero as we get further away
		 */
		float lmax = 3 * Cloud.LIFT_UNIT;
		float dh = (float) 0.2;
		float z = getHeight(p[0], p[1]);
		float h = p[2] - z;
		float h_factor = (float) Math.sqrt(z / h0);
		// if (p.z > maxH + (float) 0.2) return 0;
		if (orientation == OR_X) {
			if (p[1] <= y0) {
				// System.out.println("ground: " + z);
				if (h < dh) {
					return lmax * h_factor;
				} else if (h < (float) 1 + dh) {
					float f = (2 + dh - h) / 2;
					return (float) ((f * f) * lmax) * h_factor;
				} else {
					return 0;
				}
			} else if ((p[2] - h0) / (p[1] - y0) > h0 / tileWidth / 2) {
				float f = (2 + dh - h) / 2;
				return (float) ((f * f) * lmax);
			} else {
				return 0;
			}
		} else {
			if (p[0] <= x0) {
				// System.out.println("ground: " + z);
				if (h < dh) {
					return lmax * h_factor;
				} else if (h < (float) 1 + dh) {
					float f = (2 + dh - h) / 2;
					return (float) ((f * f) * lmax) * h_factor;
				} else {
					return 0;
				}
			} else if ((p[2] - h0) / (p[0] - x0) > h0 / tileWidth / 2) {
				float f = (2 + dh - h) / 2;
				return (float) ((f * f) * lmax);
			} else {
				return 0;
			}

		}
	}

	public void destroyMe() {
		obj3d.destroyMe();
	}

	@Override
	public float[] getP() {
		// TODO Auto-generated method stub
		return new float[] { x0, y0, 0 };
	}

	@Override
	public boolean isActive() {
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	public boolean isActive(float t) {
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	public float getLift() {
		// TODO Auto-generated method stub
		return 2.8f * Cloud.LIFT_UNIT;
	}

}
