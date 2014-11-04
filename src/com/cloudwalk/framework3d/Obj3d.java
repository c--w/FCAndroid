/*
  @(#)Obj3d.java (part of 'Flight Club')
	
  This code is covered by the GNU General Public License
  detailed at http://www.gnu.org/copyleft/gpl.html
	
  Flight Club docs located at http://www.danb.dircon.co.uk/hg/hg.htm
  Copyright 2001-2002 Dan Burton <danb@dircon.co.uk>
 */
package com.cloudwalk.framework3d;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StreamTokenizer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Vector;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

/**
 * This class implements a 3d object made up of triangles. We have a list of points (x, y, z) in model space and a second list of the those points after they
 * have been mapped into screen space (x_, y_, z_). The x_ coord is handy for fogging and sorting; it represents the 'depth' of the projected point. (y_, z_) is
 * the location of the point in screen space.
 * 
 * <p>
 * The mapping from model space to screen space is as follows:
 * 
 * 1. A translation such that the camera focus becomes the origin. 2. A rotation such that the camera eye is on the +ve x_ axis. 3. Forshorten y_ and z_ by one
 * over x_, the depth of the point. 4. Scale y_ and z_ according to the height of the display area in pixels.
 * 
 * We use a flat array to store the coords as follows: (x0, y0, z0, x1, y1, z1, x2, ...). If npoints * 3 exceeds the array size then we double the array size
 * and do a System.copyarray().
 * 
 * I got the flat array idea from the WireFrame demo applet.
 */
public class Obj3d implements CameraSubject {
	protected ModelViewer modelViewer;
	float[] ps;

	int npoints = 0;
	int maxpoints;
	Triangle[] triangles;
	Triangle[] shadows;
	private int polynext = 0;
	private int shadownext = 0;
	Polywire[] polywires = new Polywire[0]; // hhm, tagged these on
	private int wirenext = 0;
	boolean noShade = false; // hack for sky

	/** Bounding box in model space */
	BB box = new BB();
	float x_min, x_max;

	// I *like* gray
	public static final int COLOR_DEFAULT = Color.GRAY;


	/**
	 * Creates an Obj3d that may or may not be registed with the 3d object manager. Only registered objects are drawn on the screen. This constructor is private
	 * because it is only used by the parser. Everyone else should call the public constructor below which specifies the number of triangles that are used to
	 * model this object.
	 */
	private Obj3d(ModelViewer modelViewer, boolean register) {
		this.modelViewer = modelViewer;
		if (register) {
			registerObject3d();
		}

		// maxpoints is not a real limit because if we hit it we
		// double it
		maxpoints = 20;
		ps = new float[maxpoints * 3];
	}

	public Obj3d(ModelViewer modelViewer, int npolygons, boolean register) {
		this(modelViewer, register);
		setNumPolygons(npolygons);
	}

	private void setNumPolygons(int n) {
		triangles = new Triangle[n];
	}

	// hack for glider tails !
	public void setNumPolywires(int n) {
		polywires = new Polywire[n];
	}

	/** Creates an Obj3d and registers it with the 3d object manager. */
	public Obj3d(ModelViewer modelViewer, int npolygons) {
		this(modelViewer, npolygons, true);
	}

	/** Creates a copy of <code>from</code>. */
	public Obj3d(Obj3d from, boolean register) {
		this(from.modelViewer, from.triangles.length, register);

		// make our storage same size as from's
		maxpoints = from.maxpoints;
		npoints = from.npoints;
		polynext = from.polynext;
		ps = new float[maxpoints * 3];

		// copy co-ords data
		System.arraycopy(from.ps, 0, ps, 0, npoints * 3);

		// copy triangles
		for (int i = 0; i < from.triangles.length; i++) {
			Triangle fromPoly = from.triangles[i];
			triangles[i] = new Triangle(fromPoly.c);
			System.arraycopy(fromPoly.points, 0, triangles[i].points, 0, fromPoly.points.length);
		}
	}

	/**
	 * Parses a text file and creates an Obj3d. The file format is as follows:
	 * 
	 * <pre>
	 * 
	 * 	# a simple kite made of two triangular triangles
	 * 	# by foo bar, 27 Aug 2002
	 * 	2
	 * 	t f CC99FF 12.1 2.4 3.5, 12.4 2.2 3.5, 3.2 5 7.4, 
	 * 	t t 336699 9.7 0 3.5, 2.2 2.2 3.5, 3.2 5 7.3,
	 * </pre>
	 * 
	 * The above represents an Obj3d with two triangles, the second of which is double sided; each is a differnt color; each is made up of 3 points. Both cast
	 * shadows. The first flag indicates if the polygon casts a shadow. The second flag indicates if the polygon is double sided.
	 */
	public Obj3d(StreamTokenizer st, ModelViewer modelViewer, boolean register) throws IOException, FileFormatException {

		// call to constructor must be first, but we do not
		// know how many triangles yet, hence the following
		this(modelViewer, register);

		// gobble EOL's due to comments
		while (st.nextToken() == StreamTokenizer.TT_EOL) {
			;
		}

		// how many triangles
		int num = (int) st.nval;

		// set num triangles
		this.setNumPolygons(num);

		// gobble up new line
		st.nextToken();

		// Tools3d.debugTokens(st); //tmp
		// System.exit(0);

		// read a line a data for each polygon
		for (int i = 0; i < num; i++) {

			// has shadow ?
			st.nextToken();
			boolean shadow = "t".equals(st.sval);

			// double sided ?
			st.nextToken();
			boolean doubleSided = "t".equals(st.sval);

			// color - a hex number
			int color = 0;
			st.nextToken();
			try {
				color = Color.parseColor("#" + st.sval);
			} catch (NumberFormatException e) {
				throw new FileFormatException("Unable to parse color: " + st.sval + ", polygon index: " + i);
			}

			if (color == 0) {
				color = COLOR_DEFAULT;
			}

			// read point co-ords until we reach end of line
			Vector points = new Vector();
			while (st.nextToken() != StreamTokenizer.TT_EOL) {

				float[] p = new float[3];

				p[0] = (float) st.nval;
				st.nextToken();
				p[1] = (float) st.nval;
				st.nextToken();
				p[2] = (float) st.nval;

				// gobble up comma which seperates points
				// note there must be a comma after the last point
				if (st.nextToken() != ',') {
					throw new FileFormatException("Unable to parse co-ordinates; comma expected: " + st);
				}
				points.addElement(p);
			}

			// convert vector to an array
			float[][] vs = new float[points.size()][];
			for (int j = 0; j < points.size(); j++) {
				vs[j] = (float[]) points.elementAt(j);
			}

			// finally, add the polygon
			if (!shadow) {
				this.addPolygon(vs, color, doubleSided);
			} else {
				this.addPolygonWithShadow(vs, color, doubleSided);
			}
		}
	}

	/**
	 * This method is a hack. I really want another constructor.
	 */
	public static Obj3d parseFile(InputStream is, ModelViewer modelViewer, boolean register) throws IOException, FileFormatException {

		StreamTokenizer st = new StreamTokenizer(new BufferedReader(new InputStreamReader(is)));
		st.eolIsSignificant(true);
		st.commentChar('#');

		Obj3d obj3d = new Obj3d(st, modelViewer, register);

		// should be at end of file
		is.close();
		if (st.ttype != StreamTokenizer.TT_EOF)
			throw new FileFormatException(is.toString());

		return obj3d;
	}

	public void destroyMe() {
		modelViewer.obj3dManager.removeObj(this);
	}

	private void registerObject3d() {
		modelViewer.obj3dManager.addObj(this);
	}

	/**
	 * Draws a 2d representation of the object onto the screen.
	 * 
	 * @see ModelCanvas#paintModel
	 */
	public void draw(float[] mMVPMatrix, float[] mViewMatrix, float[] mModelMatrix, float[] mProjectionMatrix, int mMVPMatrixHandle) {
		try {

			//drawShadow(g);

			for (int i = 0; i < triangles.length; i++) {
				triangles[i].drawTriangle(mMVPMatrix, mViewMatrix, mModelMatrix, mProjectionMatrix, mMVPMatrixHandle);
			}

			for (int i = 0; i < polywires.length; i++) {
				polywires[i].drawLines(mMVPMatrix, mViewMatrix, mModelMatrix, mProjectionMatrix, mMVPMatrixHandle);
			}
		} catch (Exception e) {
			Log.e("FC OBJ3D draw", e.getMessage(), e);
		}
	}

	public void translateBy(float dx, float dy, float dz) {
		for (int i = 0; i < npoints * 3; i += 3) {
			ps[i] = ps[i] + dx;
			ps[i + 1] = ps[i + 1] + dy;
			ps[i + 2] = ps[i + 2] + dz;
		}
		// reset bounding box
		this.box.translateBy(dx, dy, dz);
	}

	public void scaleBy(float s) {
		for (int i = 0; i < npoints * 3; i += 3) {
			ps[i] *= s;
			ps[i + 1] *= s;
			ps[i + 2] *= s;
		}
		// reset bounding box
		this.box.scaleBy(s);
	}

	/** Gives all triangles the specifed color. */
	public void setColor(int c) {
		for (int i = 0; i < triangles.length; i++) {
			triangles[i].c = c;
		}
	}

	/**
	 * Adds a point to the list. First scan thru' the list to see if we already have a point with identical coords. If so, return index of that point.
	 */
	int addPoint(float x, float y, float z) {
		int index = -1;
		for (int i = 0; i < npoints * 3; i += 3) {
			if (ps[i] == x && ps[i + 1] == y && ps[i + 2] == z) {
				index = i;
				break;
			}
		}

		if (index != -1) {
			// we already have this point - just return its index
			return index;
		} else {
			// add point
			if (npoints >= maxpoints) {
				// double array size
				maxpoints *= 2;
				float[] qs = new float[maxpoints * 3];
				System.arraycopy(ps, 0, qs, 0, ps.length);
				ps = qs;
			}

			int i = npoints * 3;
			ps[i] = x;
			ps[i + 1] = y;
			ps[i + 2] = z;

			npoints++;
			return i;
		}
	}

	/**
	 * Adds a polygon. Note that the vertices of the polygon are passed using a float[][] and NOT a float[]. Eg.
	 * 
	 * {{x0, y0, z0}, {x1, y1, z1}, ...}
	 * 
	 * We *flatten* the data once it is encapsulated inside this class; outside this class we want clarity; inside this class we want speed !
	 */
	public int addPolygon(float[][] vs, int c, boolean doubleSided) {
		Triangle polygon = new Triangle(c);
		for (int i = 0; i < vs.length; i++) {
			polygon.addPoint(this.addPoint(vs[i][0], vs[i][1], vs[i][2])); // TODO split to triangles
		}
		triangles[polynext] = polygon;

		// if this was the last polygon then we may now set the
		// bounding box
		if (polynext == triangles.length - 1) {
			this.box.setBB();
		}
		return polynext++;
	}

	/**
	 * Adds a polygon with one side visible. Which of the two sides is visible is determined by the order of the points. If as you look at the polygon the
	 * points go clockwise then the normal points away from you.
	 */
	public int addPolygon(float[][] vs, int c) {
		return addPolygon(vs, c, false);
	}

	/** Adds a single sided gray polygon. */
	public int addPolygon(float[][] vs) {
		return addPolygon(vs, COLOR_DEFAULT, false);
	}

	/** Adds a polygon that is visible from both sides. */
	public int addPolygon2(float[][] vs, int c) {
		return addPolygon(vs, c, true);
	}

	/** Adds a gray polygon that is visible from both sides. */
	public int addPolygon2(float[][] vs) {
		return addPolygon(vs, COLOR_DEFAULT, true);
	}


	/**
	 * Adds a series of triangles by rotating the points list (ps) steps times, and colouring them as defined by the color array (stripes!).
	 */
	public void lathePolygons(float[][] ps, int[] color, int steps) {
		float _ps[][] = new float[ps.length][3];
		float stepAngle = (float) Math.PI * 2 / steps; // the angle of each step
														// in radians
		int currColor = 0;
		float m[][];

		for (int step = 0; step < steps; step++) {

			// translate each point to it next step round
			m = Tools3d.rotateAboutZ(stepAngle);
			for (int i = 0; i < ps.length; i++) {
				Tools3d.applyTo(m, ps[i], _ps[i]);
			}

			// build the triangles
			for (int point = 0; point < ps.length - 1; point++) {
				this.addPolygon(new float[][] { _ps[point], ps[point], ps[point + 1], _ps[point + 1] }, color[currColor]);
			}

			// copy the translated array to the starting array read for the next
			// iteration
			for (int i = 0; i < ps.length; i++) {
				ps[i][0] = _ps[i][0];
				ps[i][1] = _ps[i][1];
				ps[i][2] = _ps[i][2];
			}

			currColor++;
			if (currColor == color.length)
				currColor = 0;
		}
	}

	/** Adds a wire (eg. glider tails) */
	public int addPolywire(float[][] vs, int c, int thickness) {
		Polywire wire = new Polywire(vs.length, c, thickness);
		for (int i = 0; i < vs.length; i++) {
			wire.addPoint(this.addPoint(vs[i][0], vs[i][1], vs[i][2]));
		}
		// Log.i("FC", "" + this + " " + wirenext);
		polywires[wirenext] = wire;
		return wirenext++;
	}

	/** Adds a wire (eg. glider tails) */
	public int addPolywire(float[][] vs, int c) {
		Polywire wire = new Polywire(vs.length, c);
		for (int i = 0; i < vs.length; i++) {
			wire.addPoint(this.addPoint(vs[i][0], vs[i][1], vs[i][2]));
		}
		// Log.i("FC", "" + this + " " + wirenext);
		polywires[wirenext] = wire;
		return wirenext++;
	}

	/** Adds a closed wire (eg. an outline of a circle) */
	public int addPolywireClosed(float[][] vs, int c) {
		Polywire wire = new Polywire(vs.length + 1, c);
		for (int i = 0; i < vs.length; i++) {
			wire.addPoint(this.addPoint(vs[i][0], vs[i][1], vs[i][2]));
		}
		// close the wire by adding the first point onto its end
		wire.addPoint(this.addPoint(vs[0][0], vs[0][1], vs[0][2]));
		// Log.i("FC", "" + this + " " + wirenext);
		polywires[wirenext] = wire;
		return wirenext++;
	}

	/**
	 * Reverses the ordering of the triangles that make up this object. This may be useful for fudging the z order before drawing the object.
	 */
	void reverse() {
		for (int i = 0; i < triangles.length / 2 - 1; i++) {
			int j = triangles.length - 1 - i;
			Triangle p = triangles[i];
			triangles[i] = triangles[j];
			triangles[j] = p;
		}
	}

	/**
	 * Creates a unit cube whose centre of mass lies at the origin.
	 */
	public static Obj3d makeCube(ModelViewer modelViewer) {

		Obj3d o = new Obj3d(modelViewer, 6);

		/**
		 * Two steps. First we create a cube whose corner is at the origin and then we translate it so that its center is at the origin.
		 */

		// front (x=1) then back (x=0)
		o.addPolygon(new float[][] { { 1, 0, 0 }, { 1, 1, 0 }, { 1, 1, 1 }, { 1, 0, 1 } }, Color.RED);
		o.addPolygon(new float[][] { { 0, 0, 0 }, { 0, 0, 1 }, { 0, 1, 1 }, { 0, 1, 0 } }, Color.GRAY);

		// right (y=1) then left (y=0)
		o.addPolygon(new float[][] { { 0, 1, 0 }, { 0, 1, 1 }, { 1, 1, 1 }, { 1, 1, 0 } }, Color.GREEN);
		o.addPolygon(new float[][] { { 0, 0, 0 }, { 1, 0, 0 }, { 1, 0, 1 }, { 0, 0, 1 } }, Color.YELLOW);

		// top (z=1) then bottom (z=0)
		o.addPolygon(new float[][] { { 0, 0, 1 }, { 1, 0, 1 }, { 1, 1, 1 }, { 0, 1, 1 } }, Color.BLUE);
		o.addPolygon(new float[][] { { 0, 0, 0 }, { 0, 1, 0 }, { 1, 1, 0 }, { 1, 0, 0 } }, Color.MAGENTA);

		o.translateBy(-0.5f, -0.5f, -0.5f);
		return o;
	}

	public void setBB() {
		this.box.setBB();
	}

	public float[] getEye() {
		return this.box.getEye();
	}

	public float[] getFocus() {
		return this.box.getCenter();
	}

	/** Gets the index of a point on a polygon. */
	public int getPointIndex(int poly, int vertex) {
		return triangles[poly].points[vertex];
	}

	/** Gets the index of a point on a polywire. (See Tail.java) */
	public int getPointIndex2(int poly, int vertex) {
		return polywires[poly].points[vertex];
	}

	/** Sets the co-ords of a point. */
	public void setPoint(int index, float x, float y, float z) {
		ps[index] = x;
		ps[index + 1] = y;
		ps[index + 2] = z;
	}

	public final float getDepthMin() {
		return x_min;
	}

	public final float getDepthMax() {
		return x_max;
	}

	/**
	 * Dumps the data of this 3d shape. public String toString() {
	 * 
	 * StringBuffer sb = new StringBuffer();
	 * 
	 * // number of triangles sb.append(triangles.length + "\n");
	 * 
	 * // loop - one line per polygon for (int i = 0; i < triangles.length; i++) { Polygon po = triangles[i];
	 * 
	 * // todo: tidy up shadows - what a mess ! boolean shadow = false; for (int j = 0; j < MAX_SHADOWS; j++) { if (shadowCasters[j] == i) { shadow = true;
	 * break; } } sb.append(shadow ? "t " : "f ");
	 * 
	 * sb.append(po.doubleSided ? "t " : "f ");
	 * 
	 * int rgb = po.c; sb.append("\"" + Integer.toHexString(rgb & 0xFFFFFF) + "\" ");
	 * 
	 * // loop - points in this polygon for (int j = 0; j < po.n; j++) { int index = po.points[j]; sb.append(Tools3d.round(ps[index]) + " " +
	 * Tools3d.round(ps[index + 1]) + " " + Tools3d.round(ps[index + 2]) + ", "); }
	 * 
	 * // finished this polygon so end line sb.append("\n"); } return new String(sb); }
	 */

	
	/**
	 * Adds a polygon. And then add a second polygon - the shadow - using the same points with z ~ 0
	 */
	public int addPolygonWithShadow(float[][] vs, int c, boolean doubleSided) {
		// create shadow
		float[][] vs_ = new float[vs.length][3];

		// reverse point order so shadow faces
		// up ?? assumes shadow caster faces down always ??
		int j = 0;
		for (int i = vs.length - 1; i >= 0; i--) {
			float[] p = vs[i];
			float[] q = new float[] { p[0], p[1], 0.00013f }; // change z so we
																// get unique
																// points
			vs_[j++] = q;
		}

		int color = Color.rgb(200, 200, 200);
		// Color color = new Color(80, 170, 80);
		shadows[shadownext] = new Triangle(color);

		for (int i = 0; i < vs_.length; i++) {
			int pointIndex = this.addPoint(vs_[i][0], vs_[i][1], vs_[i][2]);
			shadows[shadownext].addPoint(pointIndex);
		}
		// Log.i("FC obj3d", shadows[numShadows].toString());
		shadownext++;
		return shadownext;
	}

	/**
	 * Keeps the shadow under its object. The owner/creator of this object should call this method each time they move the object.
	 */
	public void updateShadow() {
		for (int i = 0; i < triangles.length; i++) {
			Triangle polygon = triangles[i];

			for (int j = 2; j >= 0; j--) {
				float x = ps[polygon.points[j]];
				float y = ps[polygon.points[j] + 1];
				int index = shadows[i].points[2 - j];
				ps[index] = x;
				ps[index + 1] = y;
				ps[index + 2] = 0.00012f;

				// old
				/*
				 * float[] p = (Vector3d) points.elementAt(surface.points[j]); float[] q = (Vector3d) points.elementAt(shadows[i].points[surface.numPoints - 1 -
				 * j]);//?? Tools3d.clone(p, q); if (app.landscape != null) q.z = app.landscape.getHeight(q.x, q.y); else q.z = 0;
				 */
			}
		}
	}

	/**
	 * This inner class represents a polygon. The polygon is made from N points (or vertices). It is either only visible from one side or it is visible from
	 * both sides.
	 */
	class Triangle {
		private FloatBuffer vertices=null;

		/** This will be used to pass in model position information. */
		private int mPositionHandle;

		/** This will be used to pass in model color information. */
		private int mColorHandle;

		/** How many bytes per float. */
		private final int mBytesPerFloat = 4;

		/** How many elements per vertex. */
		private final int mStrideBytes = 7 * mBytesPerFloat;

		/** Offset of the position data. */
		private final int mPositionOffset = 0;

		/** Size of the position data in elements. */
		private final int mPositionDataSize = 3;

		/** Offset of the color data. */
		private final int mColorOffset = 3;

		/** Size of the color data in elements. */
		private final int mColorDataSize = 4;

		float[] verticesData = null;		
		
		int[] points; // list of indexes for the points that make up this
						// Polygon
		int next = 0;
		int c; // true color

		public Triangle(int color) {
			points = new int[3];
			verticesData = new float[3*mStrideBytes];
			c = color;
		}

		public void addPoint(int i) {
			points[next] = i;
			verticesData[next*7+0] = ps[points[i]+0];
			verticesData[next*7+1] = ps[points[i]+1];
			verticesData[next*7+2] = ps[points[i]+2];
			verticesData[next*7+3] = Color.red(c)/255f;
			verticesData[next*7+4] = Color.green(c)/255f;
			verticesData[next*7+5] = Color.blue(c)/255f;
			verticesData[next*7+6] = 1f;
			next++;
			if(next==3) {
				fillVerticesData();
			}
		}

		private void fillVerticesData() {
			vertices = ByteBuffer.allocateDirect(verticesData.length * mBytesPerFloat).order(ByteOrder.nativeOrder()).asFloatBuffer();
			vertices.put(verticesData).position(0);
		}
		

		/**
		 * Draws this triangle on the screen.
		 */
		void drawTriangle(float[] mMVPMatrix, float[] mViewMatrix, float[] mModelMatrix, float[] mProjectionMatrix, int mMVPMatrixHandle) {
			// Pass in the position information
			vertices.position(mPositionOffset);
			GLES20.glVertexAttribPointer(mPositionHandle, mPositionDataSize, GLES20.GL_FLOAT, false, mStrideBytes, vertices);

			GLES20.glEnableVertexAttribArray(mPositionHandle);

			// Pass in the color information
			vertices.position(mColorOffset);
			GLES20.glVertexAttribPointer(mColorHandle, mColorDataSize, GLES20.GL_FLOAT, false, mStrideBytes, vertices);

			GLES20.glEnableVertexAttribArray(mColorHandle);
			if(mModelMatrix == null) {
				mModelMatrix = new float[16];
				Matrix.setIdentityM(mModelMatrix, 0);
			}
			// This multiplies the view matrix by the model matrix, and stores the result in the MVP matrix
			// (which currently contains model * view).
			Matrix.multiplyMM(mMVPMatrix, 0, mViewMatrix, 0, mModelMatrix, 0);

			// This multiplies the modelview matrix by the projection matrix, and stores the result in the MVP matrix
			// (which now contains model * view * projection).
			Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mMVPMatrix, 0);

			GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mMVPMatrix, 0);
			GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3);
		}

		public String toString() {
			return "points: " + Arrays.toString(points);
		}

	}

	/**
	 * This inner class represents a wire. The wire is made from N points (or vertices).
	 */
	class Polywire {
		private FloatBuffer vertices=null;

		/** This will be used to pass in model position information. */
		private int mPositionHandle;

		/** This will be used to pass in model color information. */
		private int mColorHandle;

		/** How many bytes per float. */
		private final int mBytesPerFloat = 4;

		/** How many elements per vertex. */
		private final int mStrideBytes = 7 * mBytesPerFloat;

		/** Offset of the position data. */
		private final int mPositionOffset = 0;

		/** Size of the position data in elements. */
		private final int mPositionDataSize = 3;

		/** Offset of the color data. */
		private final int mColorOffset = 3;

		/** Size of the color data in elements. */
		private final int mColorDataSize = 4;

		float[] verticesData = null;		

		int n; // number of points eg. 4 for a square
		int[] points; // list of indexes for the points that make up this
						// Polygon
		int next = 0;
		int c; // true color
		int thickness;

		public Polywire(int n, int color) {
			this(n, color, 0);
		}

		public Polywire(int n, int color, int thickness) {
			this.n = n;
			points = new int[n];
			verticesData = new float[n*mStrideBytes];
			this.thickness = thickness;
			c = color;
		}

		public void addPoint(int i) {
			points[next] = i;
			verticesData[next*7+0] = ps[points[i]+0];
			verticesData[next*7+1] = ps[points[i]+1];
			verticesData[next*7+2] = ps[points[i]+2];
			verticesData[next*7+3] = Color.red(c)/255f;
			verticesData[next*7+4] = Color.green(c)/255f;
			verticesData[next*7+5] = Color.blue(c)/255f;
			verticesData[next*7+6] = 1f;
			next++;
			if(next==n) {
				fillVerticesData();
			}
		}

		private void fillVerticesData() {
			vertices = ByteBuffer.allocateDirect(verticesData.length * mBytesPerFloat).order(ByteOrder.nativeOrder()).asFloatBuffer();
			vertices.put(verticesData).position(0);
		}


		/**
		 * Draws this polywire on the screen. If a point is not visible (ie. behind the camera) then do not attempt to draw the line connecting that point.
		 * 
		 * Loop thru the list and draw a line connecting each point to the next.
		 */
		/**
		 * Draws this triangle on the screen.
		 */
		void drawLines(float[] mMVPMatrix, float[] mViewMatrix, float[] mModelMatrix, float[] mProjectionMatrix, int mMVPMatrixHandle) {
			// Pass in the position information
			vertices.position(mPositionOffset);
			GLES20.glVertexAttribPointer(mPositionHandle, mPositionDataSize, GLES20.GL_FLOAT, false, mStrideBytes, vertices);

			GLES20.glEnableVertexAttribArray(mPositionHandle);

			// Pass in the color information
			vertices.position(mColorOffset);
			GLES20.glVertexAttribPointer(mColorHandle, mColorDataSize, GLES20.GL_FLOAT, false, mStrideBytes, vertices);

			GLES20.glEnableVertexAttribArray(mColorHandle);
			if(mModelMatrix == null) {
				mModelMatrix = new float[16];
				Matrix.setIdentityM(mModelMatrix, 0);
			}
			// This multiplies the view matrix by the model matrix, and stores the result in the MVP matrix
			// (which currently contains model * view).
			Matrix.multiplyMM(mMVPMatrix, 0, mViewMatrix, 0, mModelMatrix, 0);

			// This multiplies the modelview matrix by the projection matrix, and stores the result in the MVP matrix
			// (which now contains model * view * projection).
			Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mMVPMatrix, 0);

			GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mMVPMatrix, 0);
			GLES20.glDrawArrays(GLES20.GL_LINES, 0, n-1);
		}

	}

	public void setNoShade() {
		noShade = true;
	}

	public float getZmax() {
		return box.zmax;
	}

	/**
	 * This class implements a bounding box in model space that contains the parent Obj3d instance.
	 */
	class BB {
		float xmin, xmax, ymin, ymax, zmin, zmax;

		public BB() {
			;
		}

		void setBB() {
			/**
			 * Loop thru' all points and find the min and max for each dimension.
			 */
			for (int i = 0; i < npoints * 3; i += 3) {
				if (i == 0) {
					xmin = xmax = ps[0];
					ymin = ymax = ps[1];
					zmin = zmax = ps[2];
				} else {
					if (ps[i] < xmin) {
						xmin = ps[i];
					} else if (ps[i] > xmax) {
						xmax = ps[i];
					}

					if (ps[i + 1] < ymin) {
						ymin = ps[i + 1];
					} else if (ps[i + 1] > ymax) {
						ymax = ps[i + 1];
					}

					if (ps[i + 2] < zmin) {
						zmin = ps[i + 2];
					} else if (ps[i + 2] > zmax) {
						zmax = ps[i + 2];
					}
				}
			}
		}

		private float longestSide() {
			float dx = xmax - xmin;
			float dy = ymax - ymin;
			float dz = zmax - zmin;

			float max = dx;
			if (dy > max)
				max = dy;
			if (dz > max)
				max = dz;
			return max;
		}

		float[] getCenter() {
			float[] c = new float[3];
			c[0] = (xmin + xmax) / 2;
			c[1] = (ymin + ymax) / 2;
			c[2] = (zmin + zmax) / 2;
			return c;
		}

		float[] getEye() {
			float max = longestSide();
			if (max < 0.1)
				max = 0.1f; // not too close
			float[] c = getCenter();
			return new float[] { c[0] + max * 0.5f, c[1] - max, c[2] + max * 0.5f };
		}

		void translateBy(float x, float y, float z) {
			xmin += x;
			xmax += x;
			ymin += y;
			ymax += y;
			zmin += z;
			zmax += z;
		}

		void scaleBy(float s) {
			xmin *= s;
			xmax *= s;
			ymin *= s;
			ymax *= s;
			zmin *= s;
			zmax *= s;
		}
	}
}
