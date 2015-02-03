/*
  @(#)Obj3d.java (part of 'Flight Club')
	
  This code is covered by the GNU General Public License
  detailed at http://www.gnu.org/copyleft/gpl.html
	
  Flight Club docs located at http://www.danb.dircon.co.uk/hg/hg.htm
  Copyright 2001-2002 Dan Burton <danb@dircon.co.uk>
 */
package com.cloudwalk.framework3d;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.graphics.Color;
import android.opengl.GLES20;
import android.util.Log;

import com.cloudwalk.client.Task;

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
public class Obj3dStatic {
	public static float[] ps;
	public static int[] colors;

	static int npoints;
	static int maxpoints;
	static List<Triangle> triangles;
	static List<Polywire> polywires;
	static private int wirenext = 0;

	static int COLOR_SHADOW = Color.argb(128, 220, 220, 220);
	public static final int COLOR_DEFAULT = Color.BLACK;
	static int num_points_wire;

	public static boolean static_initialized = false;
	public static boolean static_initialized_wire = false;

	public static void init() {
		npoints = 0;
		maxpoints = 200;
		ps = new float[maxpoints * 3];
		colors = new int[maxpoints];
		wirenext = 0;
		triangles = new ArrayList<Obj3dStatic.Triangle>();
		polywires = new ArrayList<Polywire>();

		static_initialized = false;
		static_initialized_wire = false;
		num_points_wire = 0;
	}

	/**
	 * Creates an Obj3d that may or may not be registed with the 3d object manager. Only registered objects are drawn on the screen. This constructor is private
	 * because it is only used by the parser. Everyone else should call the public constructor below which specifies the number of triangles that are used to
	 * model this object.
	 */

	/*
	 * Draws a 2d representation of the object onto the screen.
	 * 
	 * @see ModelCanvas#paintModel
	 */
	public static void draw(float[] mMVPMatrix, int mMVPMatrixHandle, int mPositionHandle, int mColorHandle, int mNormalHandle) {
		try {
			drawTriangles(mMVPMatrix, mMVPMatrixHandle, mPositionHandle, mColorHandle, mNormalHandle);
			drawLines(mMVPMatrix, mMVPMatrixHandle, mPositionHandle, mColorHandle, mNormalHandle);
		} catch (Exception e) {
			Log.e("FC OBJ3D draw", e.getMessage(), e);
		}
	}

	/**
	 * Adds a point to the list. First scan thru' the list to see if we already have a point with identical coords. If so, return index of that point.
	 */

	public static int addPoint(float x, float y, float z, int color) {
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
				maxpoints *= 1.5f;
				float[] qs = new float[maxpoints * 3];
				int[] qc = new int[maxpoints];
				System.arraycopy(ps, 0, qs, 0, ps.length);
				System.arraycopy(colors, 0, qc, 0, colors.length);
				ps = qs;
				colors = qc;
			}

			int i = npoints * 3;
			ps[i] = x;
			ps[i + 1] = y;
			ps[i + 2] = z;
			colors[npoints] = color;
			npoints++;
			return i;
		}
	}

	/**
	 * Adds a polygon. Note that the verticesFB of the polygon are passed using a float[][] and NOT a float[]. Eg.
	 * 
	 * {{x0, y0, z0}, {x1, y1, z1}, ...}
	 * 
	 * We *flatten* the data once it is encapsulated inside this class; outside this class we want clarity; inside this class we want speed !
	 */
	public static void addPolygon(float[][] vs, int c, boolean doubleSided, boolean shadow) {
		Triangle polygon = new Triangle();
		int j = 0;
		for (int i = 0; i < vs.length; i++) {
			if (j % 3 == 0 && j != 0) {
				addTriangle(polygon, shadow);
				polygon = new Triangle();
				polygon.addPoint(addPoint(vs[0][0], vs[0][1], vs[0][2], c));
				i -= 2;
				j = 0;
			} else {
				polygon.addPoint(addPoint(vs[i][0], vs[i][1], vs[i][2], c));
			}
			j++;
		}
		if (!doubleSided)
			polygon.part = 1;
		addTriangle(polygon, shadow);

		return;
	}

	static void addTriangle(Triangle triangle, boolean shadow) {
		triangles.add(triangle);
		if (shadow)
			addShadow(triangle);
	}

	public static void addShadow(Triangle triangle) {
		Triangle shadow = new Triangle();
		shadow.shadow = true;

		System.arraycopy(triangle.points, 0, shadow.points, 0, triangle.points.length);
		if (Tools3d.dot(new float[] { ps[triangle.points[0]] - ps[triangle.points[1]], ps[triangle.points[0] + 1] - ps[triangle.points[1] + 1],
				ps[triangle.points[0] + 2] - ps[triangle.points[1] + 2] }, new float[] { ps[triangle.points[0]] - ps[triangle.points[2]],
				ps[triangle.points[0] + 1] - ps[triangle.points[2] + 1], ps[triangle.points[0] + 2] - ps[triangle.points[2] + 2] }) > 0) {
			shadow.points[0] = triangle.points[2];
			shadow.points[2] = triangle.points[0];
		}
		shadow.updateVertices(true);
		triangles.add(shadow);
		return;
	}

	/**
	 * Adds a polygon with one side visible. Which of the two sides is visible is determined by the order of the points. If as you look at the polygon the
	 * points go clockwise then the normal points away from you.
	 */
	public static void addPolygon(float[][] vs, int c) {
		addPolygon(vs, c, false, false);
	}

	/** Adds a single sided gray polygon. */
	public static void addPolygon(float[][] vs) {
		addPolygon(vs, COLOR_DEFAULT, false, false);
	}

	/** Adds a polygon that is visible from both sides. */
	public static void addPolygon2(float[][] vs, int c) {
		addPolygon(vs, c, true, false);
	}

	/** Adds a gray polygon that is visible from both sides. */
	public static void addPolygon2(float[][] vs) {
		addPolygon(vs, COLOR_DEFAULT, true, false);
	}

	/** Gets the index of a point on a polygon. */
	public static int getPointIndex(int poly, int vertex) {
		return triangles.get(poly).points[vertex];
	}

	/** Adds a wire (eg. glider tails) */
	public static int addPolywire(float[][] vs, int c, int thickness) {
		Polywire wire = new Polywire(vs.length, c, thickness);
		for (int i = 0; i < vs.length; i++) {
			wire.addPoint(addPoint(vs[i][0], vs[i][1], vs[i][2], c));
		}
		// Log.i("FC", "" + this + " " + wirenext);
		polywires.add(wire);
		return wirenext++;
	}

	/** Adds a wire (eg. glider tails) */
	public static int addPolywire(float[][] vs, int c) {
		Polywire wire = new Polywire(vs.length, c);
		for (int i = 0; i < vs.length; i++) {
			wire.addPoint(addPoint(vs[i][0], vs[i][1], vs[i][2], c));
		}
		// Log.i("FC", "" + this + " " + wirenext);
		polywires.add(wire);
		return wirenext++;
	}

	/** Adds a closed wire (eg. an outline of a circle) */
	public static int addPolywireClosed(float[][] vs, int c) {
		Polywire wire = new Polywire(vs.length + 1, c);
		for (int i = 0; i < vs.length; i++) {
			wire.addPoint(addPoint(vs[i][0], vs[i][1], vs[i][2], c));
		}
		// close the wire by adding the first point onto its end
		wire.addPoint(addPoint(vs[0][0], vs[0][1], vs[0][2], c));
		Log.i("FC WIRE", "" + wirenext);
		polywires.add(wire);
		return wirenext++;
	}

	/** Sets the co-ords of a point. */
	public static void setPoint(int index, float x, float y, float z) {
		ps[index] = x;
		ps[index + 1] = y;
		ps[index + 2] = z;
	}

	static private FloatBuffer verticesFB = null;
	static private FloatBuffer colorsFB = null;
	static private FloatBuffer normalsFB = null;

	static private int verticesFBIdx = 0;
	static private int colorsFBIdx = 0;
	static private int normalsFBIdx = 0;

	/** How many bytes per float. */
	static private final int mBytesPerFloat = 4;
	static private final int mBytesPerShort = 2;

	/** How many elements per vertex. */
	static private final int mStrideBytes = 3 * mBytesPerFloat;

	/** Offset of the position data. */
	static private final int mPositionOffset = 0;

	/** Size of the position data in elements. */
	static private final int mPositionDataSize = 3;

	/** Offset of the normal data. */
	static private final int mNormalOffset = 0;

	/** Size of the normal data in elements. */
	static private final int mNormalDataSize = 3;

	/** How many elements per vertex. */
	static private final int mStrideNormalBytes = 3 * mBytesPerFloat;

	/** Offset of the color data. */
	static private final int mColorOffset = 0;

	/** Size of the color data in elements. */
	static private final int mColorDataSize = 4;

	/** How many elements per vertex. */
	static private final int mStrideColorBytes = 4 * mBytesPerFloat;

	static class Triangle {
		float[] verticesData = null;
		float[] colorsData = null;

		int[] points; // list of indexes for the points that make up this
						// Polygon
		int next = 0;
		boolean shadow;
		int part = 0;

		public Triangle() {
			points = new int[3];
			verticesData = new float[3 * 3];
			colorsData = new float[4 * 3];
		}

		public void addPoint(int i) {
			int c = colors[i / 3];
			if (shadow)
				c = COLOR_SHADOW;
			points[next] = i;
			if (shadow) {
				verticesData[next * 3 + 0] = -ps[i + 1] - ps[i + 2] * Task.shadowFactors[1];
				verticesData[next * 3 + 1] = 0.001f;
				verticesData[next * 3 + 2] = -ps[i + 0] - ps[i + 2] * Task.shadowFactors[0];
			} else {
				verticesData[next * 3 + 0] = -ps[i + 1];
				verticesData[next * 3 + 1] = ps[i + 2];
				verticesData[next * 3 + 2] = -ps[i + 0];
			}
			colorsData[next * 4 + 0] = Color.red(c) / 255f;
			colorsData[next * 4 + 1] = Color.green(c) / 255f;
			colorsData[next * 4 + 2] = Color.blue(c) / 255f;
			colorsData[next * 4 + 3] = Color.alpha(c) / 255f;
			next++;
		}

		public void updateVertices(boolean dirty_colors) {
			int i = 0;
			for (int next = 0; next < 3; next++) {
				i = points[next];
				if (shadow) {
					verticesData[next * 3 + 0] = -ps[i + 1] - ps[i + 2] * Task.shadowFactors[1];
					verticesData[next * 3 + 1] = 0.001f;
					verticesData[next * 3 + 2] = -ps[i + 0] - ps[i + 2] * Task.shadowFactors[0];
				} else {
					verticesData[next * 3 + 0] = -ps[i + 1];
					verticesData[next * 3 + 1] = ps[i + 2];
					verticesData[next * 3 + 2] = -ps[i + 0];
				}
				if (dirty_colors) {
					int c = colors[i / 3];
					if (shadow)
						c = COLOR_SHADOW;
					colorsData[next * 4 + 0] = Color.red(c) / 255f;
					colorsData[next * 4 + 1] = Color.green(c) / 255f;
					colorsData[next * 4 + 2] = Color.blue(c) / 255f;
					colorsData[next * 4 + 3] = Color.alpha(c) / 255f;
				}

			}
		}

		public String toString() {
			return "points: " + Arrays.toString(points);
		}

	}

	static float[] normal = new float[9];

	public static void fillVerticesData() {
		// Log.i("FC", Arrays.toString(verticesData));
		verticesFB = ByteBuffer.allocateDirect(9 * triangles.size() * mBytesPerFloat).order(ByteOrder.nativeOrder()).asFloatBuffer();
		colorsFB = ByteBuffer.allocateDirect(12 * triangles.size() * mBytesPerFloat).order(ByteOrder.nativeOrder()).asFloatBuffer();
		normalsFB = ByteBuffer.allocateDirect(normal.length * triangles.size() * mBytesPerFloat).order(ByteOrder.nativeOrder()).asFloatBuffer();
		for (Triangle triangle : triangles) {
			verticesFB.put(triangle.verticesData);
			if (triangle.shadow) {
				normal[0] = 0;
				normal[1] = -1;
				normal[2] = 0;
			} else {
				float va0 = triangle.verticesData[3] - triangle.verticesData[0];
				float va1 = triangle.verticesData[4] - triangle.verticesData[1];
				float va2 = triangle.verticesData[5] - triangle.verticesData[2];
				float vb0 = triangle.verticesData[6] - triangle.verticesData[0];
				float vb1 = triangle.verticesData[7] - triangle.verticesData[1];
				float vb2 = triangle.verticesData[8] - triangle.verticesData[2];
				normal[0] = normal[3] = normal[6] = va1 * vb2 - va2 * vb1;
				normal[1] = normal[4] = normal[7] = va2 * vb0 - va0 * vb2;
				normal[2] = normal[5] = normal[8] = va0 * vb1 - va1 * vb0;
			}
			normalsFB.put(normal);
			colorsFB.put(triangle.colorsData);

		}
		final int buffers[] = new int[3];
		GLES20.glGenBuffers(3, buffers, 0);

		verticesFB.flip();
		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffers[0]);
		GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, verticesFB.capacity() * mBytesPerFloat, verticesFB, GLES20.GL_STATIC_DRAW);

		normalsFB.flip();
		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffers[1]);
		GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, normalsFB.capacity() * mBytesPerFloat, normalsFB, GLES20.GL_STATIC_DRAW);

		colorsFB.flip();
		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffers[2]);
		GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, colorsFB.capacity() * mBytesPerFloat, colorsFB, GLES20.GL_STATIC_DRAW);

		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

		verticesFBIdx = buffers[0];
		normalsFBIdx = buffers[1];
		colorsFBIdx = buffers[2];

		verticesFB.limit(0);
		verticesFB = null;
		normalsFB.limit(0);
		normalsFB = null;
		colorsFB.limit(0);
		colorsFB = null;
		static_initialized = true;
		// Log.w("OBJ3D", Arrays.toString(n));
	}

	static void drawTriangles(float[] mMVPMatrix, int mMVPMatrixHandle, int mPositionHandle, int mColorHandle, int mNormalHandle) {
		if (!static_initialized) {
			fillVerticesData();
		}
		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, verticesFBIdx);
		GLES20.glEnableVertexAttribArray(mPositionHandle);
		GLES20.glVertexAttribPointer(mPositionHandle, mPositionDataSize, GLES20.GL_FLOAT, false, 0, 0);

		// Pass in the normal information
		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, normalsFBIdx);
		GLES20.glEnableVertexAttribArray(mNormalHandle);
		GLES20.glVertexAttribPointer(mNormalHandle, mNormalDataSize, GLES20.GL_FLOAT, false, 0, 0);

		// Pass in the texture information
		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, colorsFBIdx);
		GLES20.glEnableVertexAttribArray(mColorHandle);
		GLES20.glVertexAttribPointer(mColorHandle, mColorDataSize, GLES20.GL_FLOAT, false, 0, 0);

		// Clear the currently bound buffer (so future OpenGL calls do not use this buffer).
		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

		// Draw the cubes.
		GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3 * triangles.size());
	}

	/**
	 * This inner class represents a wire. The wire is made from N points (or verticesFB).
	 */

	private static FloatBuffer verticesFBWire = null;
	private static FloatBuffer colorsFBWire = null;
	private static FloatBuffer normalsFBWire = null;
	private static ShortBuffer lineIndicesSBWire = null;

	static private int verticesFBWireIdx = 0;
	static private int colorsFBWireIdx = 0;
	static private int normalsFBWireIdx = 0;
	static private int lineIndicesSBWireIdx = 0;

	static class Polywire {

		float[] verticesData = null;
		float[] colorsData = null;

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
			verticesData = new float[n * 3];
			colorsData = new float[n * 4];
			this.thickness = thickness;
			c = color;
		}

		public void addPoint(int i) {
			points[next] = i;
			verticesData[next * 3 + 0] = -ps[i + 1];
			verticesData[next * 3 + 1] = ps[i + 2];
			verticesData[next * 3 + 2] = -ps[i + 0];
			colorsData[next * 4 + 0] = Color.red(c) / 255f;
			colorsData[next * 4 + 1] = Color.green(c) / 255f;
			colorsData[next * 4 + 2] = Color.blue(c) / 255f;
			colorsData[next * 4 + 3] = 1f;
			next++;
		}

	}

	public static void fillVerticesDataWire() {

		for (int i = 0; i < polywires.size(); i++)
			num_points_wire += polywires.get(i).n;
		verticesFBWire = ByteBuffer.allocateDirect(num_points_wire * 3 * mBytesPerFloat).order(ByteOrder.nativeOrder()).asFloatBuffer();
		colorsFBWire = ByteBuffer.allocateDirect(num_points_wire * 4 * mBytesPerFloat).order(ByteOrder.nativeOrder()).asFloatBuffer();
		float[] normal = new float[] { 0, 1, 0 };
		normalsFBWire = ByteBuffer.allocateDirect(normal.length * num_points_wire * mBytesPerFloat).order(ByteOrder.nativeOrder()).asFloatBuffer();
		lineIndicesSBWire = ByteBuffer.allocateDirect((num_points_wire * 2 - polywires.size() * 2) * mBytesPerShort).order(ByteOrder.nativeOrder())
				.asShortBuffer();
		int gl_index = 0;
		for (int i = 0; i < polywires.size(); i++) {
			verticesFBWire.put(polywires.get(i).verticesData);
			colorsFBWire.put(polywires.get(i).colorsData);
			for (int next = 0; next < polywires.get(i).n; next++) {
				normalsFBWire.put(normal);
			}
			short[] indices = new short[polywires.get(i).n * 2 - 2];
			for (int j = 0; j < polywires.get(i).n - 1; j++) {
				indices[j * 2] = (short) gl_index;
				indices[j * 2 + 1] = (short) (gl_index + 1);
				gl_index++;
			}
			gl_index++;
			Log.i("FC" , Arrays.toString(indices));
			lineIndicesSBWire.put(indices);
		}
		Log.i("FC WIRE", "num_points_wire: " + num_points_wire);
		Log.i("FC WIRE", "polywires: " + polywires.size());
		Log.i("FC WIRE", "lineIndicesSBWire.capacity(): " + lineIndicesSBWire.capacity());

		final int buffers[] = new int[4];
		GLES20.glGenBuffers(4, buffers, 0);

		verticesFBWire.flip();
		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffers[0]);
		GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, verticesFBWire.capacity() * mBytesPerFloat, verticesFBWire, GLES20.GL_STATIC_DRAW);

		normalsFBWire.flip();
		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffers[1]);
		GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, normalsFBWire.capacity() * mBytesPerFloat, normalsFBWire, GLES20.GL_STATIC_DRAW);

		colorsFBWire.flip();
		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffers[2]);
		GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, colorsFBWire.capacity() * mBytesPerFloat, colorsFBWire, GLES20.GL_STATIC_DRAW);

		lineIndicesSBWire.flip();
		GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, buffers[3]);
		GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, lineIndicesSBWire.capacity() * mBytesPerShort, lineIndicesSBWire, GLES20.GL_STATIC_DRAW);

		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
		GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

		verticesFBWireIdx = buffers[0];
		normalsFBWireIdx = buffers[1];
		colorsFBWireIdx = buffers[2];
		lineIndicesSBWireIdx = buffers[3];

		verticesFBWire.limit(0);
		verticesFBWire = null;
		normalsFBWire.limit(0);
		normalsFBWire = null;
		colorsFBWire.limit(0);
		colorsFBWire = null;
		lineIndicesSBWire.limit(0);
		lineIndicesSBWire = null;
		static_initialized_wire = true;

	}

	/**
	 * Draws this polywire on the screen. If a point is not visible (ie. behind the camera) then do not attempt to draw the line connecting that point.
	 * 
	 * Loop thru the list and draw a line connecting each point to the next.
	 */
	/**
	 * Draws this triangle on the screen.
	 */
	static void drawLines(float[] mMVPMatrix, int mMVPMatrixHandle, int mPositionHandle, int mColorHandle, int mNormalHandle) {
		if (!static_initialized_wire) {
			fillVerticesDataWire();
		}

		// Pass in the position information
		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, verticesFBWireIdx);
		GLES20.glEnableVertexAttribArray(mPositionHandle);
		GLES20.glVertexAttribPointer(mPositionHandle, mPositionDataSize, GLES20.GL_FLOAT, false, 0, 0);

		// Pass in the color information
		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, colorsFBWireIdx);
		GLES20.glEnableVertexAttribArray(mColorHandle);
		GLES20.glVertexAttribPointer(mColorHandle, mColorDataSize, GLES20.GL_FLOAT, false, 0, 0);

		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, normalsFBWireIdx);
		GLES20.glEnableVertexAttribArray(mNormalHandle);
		GLES20.glVertexAttribPointer(mNormalHandle, mNormalDataSize, GLES20.GL_FLOAT, false, 0, 0);

		GLES20.glLineWidth(1);
		GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, lineIndicesSBWireIdx);
		GLES20.glDrawElements(GLES20.GL_LINES, num_points_wire * 2 - polywires.size() * 2, GLES20.GL_UNSIGNED_SHORT, 0);
		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
		GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
		// GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, n);
	}

}
