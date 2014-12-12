package com.cloudwalk.framework3d;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.preference.PreferenceManager;
import android.util.Log;

import com.cloudwalk.client.Task;
import com.cloudwalk.client.XCModelViewer;
import com.cloudwalk.framework3d.ErrorHandler.ErrorType;

/**
 * This class implements our custom renderer. Note that the GL10 parameter passed in is unused for OpenGL ES 2.0 renderers -- the static class GLES20 is used
 * instead.
 */
public class ModelViewRenderer implements GLSurfaceView.Renderer {

	private final ErrorHandler errorHandler;

	/**
	 * Store the model matrix. This matrix is used to move models from object space (where each model can be thought of being located at the center of the
	 * universe) to world space.
	 */
	private float[] mModelMatrix = new float[16];

	private float[] mLightModelMatrix = new float[16];

	/**
	 * Store the view matrix. This can be thought of as our camera. This matrix transforms world space to eye space; it positions things relative to our eye.
	 */
	private float[] mViewMatrix = new float[16];

	/** Store the projection matrix. This is used to project the scene onto a 2D viewport. */
	private float[] mProjectionMatrix = new float[16];

	/** Allocate storage for the final combined matrix. This will be passed into the shader program. */
	private float[] mMVPMatrix = new float[16];

	/** Allocate storage for the model view matrix. This will be passed into the shader program. */
	private float[] mMVMatrix = new float[16];

	/** This will be used to pass in the transformation matrix. */
	private int mMVPMatrixHandle;

	/** This will be used to pass in the transformation matrix. */
	private int mMVMatrixHandle;

	/** This will be used to pass in the light position. */
	private int mLightPosHandle;

	/** This will be used to pass in model position information. */
	int mPositionHandle;

	/** This will be used to pass in model color information. */
	int mColorHandle;

	/** This will be used to pass in model normal information. */
	int mNormalHandle;

	/** This will be used to pass in model normal information. */
	private int mFarDistanceHandle;

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

	/**
	 * Used to hold a light centered on the origin in model space. We need a 4th coordinate so we can get translations to work when we multiply this by our
	 * transformation matrices.
	 */
	private final float[] mLightPosInModelSpace = new float[] { 500000000.0f, 1000000000.0f, 500000000.0f, 1.0f };

	/** Used to hold the transformed position of the light in eye space (after transformation via modelview matrix) */
	private final float[] mLightPosInEyeSpace = new float[4];

	public XCModelViewer modelViewer;

	public float far = 100f;
	public float lastFar = 0f;
	public int width;
	public int height;
	public int viewAngle;
	boolean fancy = false;
	int sky_color = Color.WHITE;
	int ground_color = Color.WHITE;
	SharedPreferences prefs;

	/** The current heightmap object. */
	private HeightMap heightMap;

	/**
	 * Initialize the model data.
	 */
	public ModelViewRenderer(Context c, ErrorHandler errorHandler) {
		prefs = PreferenceManager.getDefaultSharedPreferences(c);
		viewAngle = Integer.parseInt(prefs.getString("view_angle", "10"));
		fancy = prefs.getBoolean("fancy", false);
		sky_color = prefs.getInt("sky_color", Color.WHITE);
		ground_color = prefs.getInt("ground_color", Color.WHITE);
		this.errorHandler = errorHandler;
	}

	public void updateCamera() {

		// Position the eye behind the origin.
		float[] eye = modelViewer.cameraMan.getEye();
		// We are looking toward the distance
		float[] focus = modelViewer.cameraMan.getFocus();
		// Set our up vector. This is where our head would be pointing were we holding the camera.
		final float upX = 0.0f;
		final float upY = 1.0f;
		final float upZ = 0.0f;

		// Set the view matrix. This matrix can be said to represent the camera position.
		// NOTE: In OpenGL 1, a ModelView matrix is used, which is a combination of a model and
		// view matrix. In OpenGL 2, we can keep track of these matrices separately if we choose.
		Matrix.setLookAtM(mViewMatrix, 0, -eye[1], eye[2], -eye[0], -focus[1], focus[2], -focus[0], upX, upY, upZ);

		// // Position the eye behind the origin.
		// final float eyeX = 0.0f;
		// final float eyeY = 0.0f;
		// final float eyeZ = 2.5f;
		//
		// // We are looking toward the distance
		// final float lookX = 0.0f;
		// final float lookY = 0.0f;
		// final float lookZ = -5.0f;

		mLightPosInModelSpace[0] = Task.sun[1];
		mLightPosInModelSpace[1] = Task.sun[2];
		mLightPosInModelSpace[2] = Task.sun[0];
		//
		// // Set our up vector. This is where our head would be pointing were we holding the camera.
		// final float upX = 0.0f;
		// final float upY = 1.0f;
		// final float upZ = 0.0f;

		// Set the view matrix. This matrix can be said to represent the camera position.
		// NOTE: In OpenGL 1, a ModelView matrix is used, which is a combination of a model and
		// view matrix. In OpenGL 2, we can keep track of these matrices separately if we choose.
		// Matrix.setLookAtM(mViewMatrix, 0, eyeX, eyeY, eyeZ, lookX, lookY, lookZ, upX, upY, upZ);

	}

	@Override
	public void onSurfaceCreated(GL10 glUnused, EGLConfig config) {
		if (fancy) {
			heightMap = new HeightMap();
			GLES20.glClearColor(Color.red(sky_color) / 255f, Color.green(sky_color) / 255f, Color.blue(sky_color) / 255f, 0f);
		} else
			GLES20.glClearColor(1f, 1f, 1f, 1f);

		updateCamera();
		final String vertexShader = "uniform mat4 u_MVPMatrix;      \n" // A constant representing the combined model/view/projection matrix.
				+ "uniform mat4 u_MVMatrix;       \n" // A constant representing the combined model/view matrix.
				+ "uniform vec3 u_LightPos;       \n" // The position of the light in eye space.

				+ "attribute vec4 a_Position;     \n" // Per-vertex position information we will pass in.
				+ "attribute vec4 a_Color;        \n" // Per-vertex color information we will pass in.
				+ "attribute vec3 a_Normal;       \n" // Per-vertex normal information we will pass in.
				+ "attribute float a_FarDist;       \n" // Per-vertex normal information we will pass in.

				+ "varying vec4 v_Color;          \n" // This will be passed into the fragment shader.

				+ "void main()                    \n" // The entry point for our vertex shader.
				+ "{                              \n"
				// Transform the vertex into eye space.
				+ "   vec3 position = vec3(u_MVMatrix * a_Position);              \n"
				// Transform the normal's orientation into eye space.
				+ "   vec3 normal = vec3(u_MVMatrix * vec4(normalize(a_Normal), 0.0));     \n"
				// Get a lighting direction vector from the light to the vertex.
				+ "   vec3 lightVector = normalize(u_LightPos - position);             \n"
				// Calculate the dot product of the light vector and vertex normal. If the normal and light vector are
				// pointing in the same direction then it will get max illumination.
				+ "   float diffuse = max(dot(normal, lightVector), 0.1);              \n" + "   vec4 color = a_Color * pow(diffuse, 0.25);						\n"

				+ "   float distance = abs(position.z);     \n"
				+ "   float factor = 1.0 - (1.0 / (1.0 + (0.08 * (100.0 / a_FarDist) * distance)));"
				+ "   vec4 fog_color = vec4(1.0,1.0,1.0,1.0);	     \n" + "   v_Color = mix(color, fog_color, factor);     \n" // Pass the color directly through
																																// the pipeline.
				+ "   gl_Position = u_MVPMatrix * a_Position;   \n" // gl_Position is a special variable used to store the final position.
				+ "                 \n" // Multiply the vertex by the matrix to get the final point in
				+ "}                              \n"; // normalized screen coordinates.

		final String fragmentShader = "precision mediump float;       \n" // Set the default precision to medium. We don't need as high of a
																			// precision in the fragment shader.

				+ "varying vec4 v_Color;          \n" // This is the color from the vertex shader interpolated across the
														// triangle per fragment.
				+ "void main()                    \n" // The entry point for our fragment shader.
				+ "{                              \n" + "   gl_FragColor = v_Color;     \n" // Pass the color directly through the pipeline.
				+ "}                              \n";

		// Load in the vertex shader.
		int vertexShaderHandle = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);

		if (vertexShaderHandle != 0) {
			// Pass in the shader source.
			GLES20.glShaderSource(vertexShaderHandle, vertexShader);

			// Compile the shader.
			GLES20.glCompileShader(vertexShaderHandle);

			// Get the compilation status.
			final int[] compileStatus = new int[1];
			GLES20.glGetShaderiv(vertexShaderHandle, GLES20.GL_COMPILE_STATUS, compileStatus, 0);

			// If the compilation failed, delete the shader.
			if (compileStatus[0] == 0) {
				GLES20.glDeleteShader(vertexShaderHandle);
				vertexShaderHandle = 0;
			}
		}

		if (vertexShaderHandle == 0) {
			throw new RuntimeException("Error creating vertex shader.");
		}

		// Load in the fragment shader shader.
		int fragmentShaderHandle = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);

		if (fragmentShaderHandle != 0) {
			// Pass in the shader source.
			GLES20.glShaderSource(fragmentShaderHandle, fragmentShader);

			// Compile the shader.
			GLES20.glCompileShader(fragmentShaderHandle);

			// Get the compilation status.
			final int[] compileStatus = new int[1];
			GLES20.glGetShaderiv(fragmentShaderHandle, GLES20.GL_COMPILE_STATUS, compileStatus, 0);

			// If the compilation failed, delete the shader.
			if (compileStatus[0] == 0) {
				GLES20.glDeleteShader(fragmentShaderHandle);
				fragmentShaderHandle = 0;
			}
		}

		if (fragmentShaderHandle == 0) {
			throw new RuntimeException("Error creating fragment shader.");
		}

		// Create a program object and store the handle to it.
		int programHandle = GLES20.glCreateProgram();

		if (programHandle != 0) {
			// Bind the vertex shader to the program.
			GLES20.glAttachShader(programHandle, vertexShaderHandle);

			// Bind the fragment shader to the program.
			GLES20.glAttachShader(programHandle, fragmentShaderHandle);

			// Bind attributes
			GLES20.glBindAttribLocation(programHandle, 0, "a_Position");
			GLES20.glBindAttribLocation(programHandle, 1, "a_Color");
			GLES20.glBindAttribLocation(programHandle, 2, "a_Normal");
			GLES20.glBindAttribLocation(programHandle, 3, "a_FarDist");

			// Link the two shaders together into a program.
			GLES20.glLinkProgram(programHandle);

			// Get the link status.
			final int[] linkStatus = new int[1];
			GLES20.glGetProgramiv(programHandle, GLES20.GL_LINK_STATUS, linkStatus, 0);

			// If the link failed, delete the program.
			if (linkStatus[0] == 0) {
				GLES20.glDeleteProgram(programHandle);
				programHandle = 0;
			}
		}

		if (programHandle == 0) {
			throw new RuntimeException("Error creating program.");
		}

		// Set program handles. These will later be used to pass in values to the program.
		mMVPMatrixHandle = GLES20.glGetUniformLocation(programHandle, "u_MVPMatrix");
		mMVMatrixHandle = GLES20.glGetUniformLocation(programHandle, "u_MVMatrix");
		mLightPosHandle = GLES20.glGetUniformLocation(programHandle, "u_LightPos");
		mPositionHandle = GLES20.glGetAttribLocation(programHandle, "a_Position");
		mColorHandle = GLES20.glGetAttribLocation(programHandle, "a_Color");
		mNormalHandle = GLES20.glGetAttribLocation(programHandle, "a_Normal");
		mFarDistanceHandle = GLES20.glGetAttribLocation(programHandle, "a_FarDist");

		// Tell OpenGL to use this program when rendering.
		GLES20.glUseProgram(programHandle);
	}

	public void updateProjectionMatrixIfNeeded() {
		this.far = modelViewer.cameraMan.getDepthOfVision();
		if (far != lastFar) {
			final float ratio = (float) width / height;
			final float left = -ratio / viewAngle;
			final float right = ratio / viewAngle;
			final float bottom = -1.0f / viewAngle;
			final float top = 1.0f / viewAngle;
			final float near = 0.2f;
			// Create a new perspective projection matrix. The height will stay the same
			// while the width will vary as per aspect ratio.

			Matrix.frustumM(mProjectionMatrix, 0, left, right, bottom, top, near, far);
			lastFar = far;
			Log.i("FC MVR", "" + far);
		}

	}

	@Override
	public void onSurfaceChanged(GL10 glUnused, int width, int height) {
		// Set the OpenGL viewport to the same size as the surface.
		GLES20.glViewport(0, 0, width, height);
		GLES20.glEnable(GLES20.GL_DEPTH_TEST);
		GLES20.glEnable(GLES20.GL_CULL_FACE);
		GLES20.glHint(GL10.GL_POLYGON_SMOOTH_HINT, GL10.GL_NICEST);
//		GLES20.glEnable(GL10.GL_POLYGON_OFFSET_FILL);
//		GLES20.glPolygonOffset(-0.1f,0);
		// GLES20.glEnable(GL10.GL_ALPHA_TEST);
		//GLES20.glEnable(GL10.GL_BLEND);
		// GLES20.glBlendFunc(GL10.GL_SRC_ALPHA_SATURATE, GL10.GL_ONE);
		// GLES20.glDepthFunc(GL10.GL_LEQUAL);
		// GLES20.glEnable(GL10.GL_SMOOTH);
		this.width = width;
		this.height = height;
		updateProjectionMatrixIfNeeded();
	}

	@Override
	public void onDrawFrame(GL10 glUnused) {
		modelViewer.clock.oneFrame(this);
	}

	public void drawEverything() {
		Matrix.setIdentityM(mModelMatrix, 0);
		GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);
		updateCamera();
		updateProjectionMatrixIfNeeded();
		// This multiplies the view matrix by the model matrix, and stores the result in the MVP matrix
		// (which currently contains model * view).
		Matrix.multiplyMM(mMVMatrix, 0, mViewMatrix, 0, mModelMatrix, 0);

		GLES20.glUniformMatrix4fv(mMVMatrixHandle, 1, false, mMVMatrix, 0);

		Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mMVMatrix, 0);

		GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mMVPMatrix, 0);

		Matrix.multiplyMV(mLightPosInEyeSpace, 0, mMVMatrix, 0, mLightPosInModelSpace, 0);

		// Pass in the light position in eye space.
		GLES20.glUniform3f(mLightPosHandle, mLightPosInEyeSpace[0], mLightPosInEyeSpace[1], mLightPosInEyeSpace[2]);

		GLES20.glVertexAttrib1f(mFarDistanceHandle, far);

		// drawWorld();
		if (fancy)
			heightMap.render();
		for (int i = 0; i < modelViewer.obj3dManager.size(); i++) {
			try {
				Obj3d o = modelViewer.obj3dManager.obj(i);
				Matrix.setIdentityM(mModelMatrix, 0);
				o.draw(mMVPMatrix, mMVPMatrixHandle, mPositionHandle, mColorHandle, mNormalHandle);
			} catch (Exception e) {
			}
		}
	}

	/** Additional constants. */
	private static final int POSITION_DATA_SIZE_IN_ELEMENTS = 3;
	private static final int NORMAL_DATA_SIZE_IN_ELEMENTS = 3;
	private static final int COLOR_DATA_SIZE_IN_ELEMENTS = 4;

	private static final int BYTES_PER_FLOAT = 4;
	private static final int BYTES_PER_SHORT = 2;

	private static final int STRIDE = (POSITION_DATA_SIZE_IN_ELEMENTS + NORMAL_DATA_SIZE_IN_ELEMENTS + COLOR_DATA_SIZE_IN_ELEMENTS) * BYTES_PER_FLOAT;

	class HeightMap {
		static final int SIZE_PER_SIDE = 64;
		static final float MIN_POSITION = -2000f;
		static final float POSITION_RANGE = 4000f;

		final int[] vbo = new int[1];
		final int[] ibo = new int[1];

		int indexCount;

		HeightMap() {
			try {
				final int floatsPerVertex = POSITION_DATA_SIZE_IN_ELEMENTS + NORMAL_DATA_SIZE_IN_ELEMENTS + COLOR_DATA_SIZE_IN_ELEMENTS;
				final int xLength = SIZE_PER_SIDE;
				final int yLength = SIZE_PER_SIDE;

				final float[] heightMapVertexData = new float[xLength * yLength * floatsPerVertex];

				int offset = 0;

				// First, build the data for the vertex buffer
				for (int y = 0; y < yLength; y++) {
					for (int x = 0; x < xLength; x++) {
						final float xRatio = x / (float) (xLength - 1);

						// Build our heightmap from the top down, so that our triangles are counter-clockwise.
						final float yRatio = 1f - (y / (float) (yLength - 1));

						final float xPosition = MIN_POSITION + (xRatio * POSITION_RANGE);
						final float yPosition = MIN_POSITION + (yRatio * POSITION_RANGE);

						// Position
						heightMapVertexData[offset++] = yPosition;
						heightMapVertexData[offset++] = -0.1f;
						heightMapVertexData[offset++] = xPosition;

						heightMapVertexData[offset++] = 0f;
						heightMapVertexData[offset++] = 1f;
						heightMapVertexData[offset++] = 0f;

						// Add some fancy colors.
						heightMapVertexData[offset++] = Color.red(ground_color) / 255f;
						heightMapVertexData[offset++] = Color.green(ground_color) / 255f;
						heightMapVertexData[offset++] = Color.blue(ground_color) / 255f;
						heightMapVertexData[offset++] = 1f;
					}
				}

				// Now build the index data
				final int numStripsRequired = yLength - 1;
				final int numDegensRequired = 2 * (numStripsRequired - 1);
				final int verticesPerStrip = 2 * xLength;

				short[] heightMapIndexData = new short[(verticesPerStrip * numStripsRequired) + numDegensRequired];

				offset = 0;

				for (int y = 0; y < yLength - 1; y++) {
					if (y > 0) {
						// Degenerate begin: repeat first vertex
						heightMapIndexData[offset++] = (short) (y * yLength);
					}

					for (int x = 0; x < xLength; x++) {
						// One part of the strip
						heightMapIndexData[offset++] = (short) ((y * yLength) + x);
						heightMapIndexData[offset++] = (short) (((y + 1) * yLength) + x);
					}

					if (y < yLength - 2) {
						// Degenerate end: repeat last vertex
						heightMapIndexData[offset++] = (short) (((y + 1) * yLength) + (xLength - 1));
					}
				}
				indexCount = heightMapIndexData.length;

				final FloatBuffer heightMapVertexDataBuffer = ByteBuffer.allocateDirect(heightMapVertexData.length * BYTES_PER_FLOAT)
						.order(ByteOrder.nativeOrder()).asFloatBuffer();
				heightMapVertexDataBuffer.put(heightMapVertexData).position(0);

				final ShortBuffer heightMapIndexDataBuffer = ByteBuffer.allocateDirect(heightMapIndexData.length * BYTES_PER_SHORT)
						.order(ByteOrder.nativeOrder()).asShortBuffer();
				heightMapIndexDataBuffer.put(heightMapIndexData).position(0);

				GLES20.glGenBuffers(1, vbo, 0);
				GLES20.glGenBuffers(1, ibo, 0);

				if (vbo[0] > 0 && ibo[0] > 0) {
					GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo[0]);
					GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, heightMapVertexDataBuffer.capacity() * BYTES_PER_FLOAT, heightMapVertexDataBuffer,
							GLES20.GL_STATIC_DRAW);

					GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo[0]);
					GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, heightMapIndexDataBuffer.capacity() * BYTES_PER_SHORT, heightMapIndexDataBuffer,
							GLES20.GL_STATIC_DRAW);

					GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
					GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
				} else {
					errorHandler.handleError(ErrorType.BUFFER_CREATION_ERROR, "glGenBuffers");
				}
			} catch (Throwable t) {
				Log.w("FC MVR", t);
				errorHandler.handleError(ErrorType.BUFFER_CREATION_ERROR, t.getLocalizedMessage());
			}
		}

		void render() {
			if (vbo[0] > 0 && ibo[0] > 0) {
				GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo[0]);

				// Bind Attributes
				GLES20.glVertexAttribPointer(mPositionHandle, POSITION_DATA_SIZE_IN_ELEMENTS, GLES20.GL_FLOAT, false, STRIDE, 0);
				GLES20.glEnableVertexAttribArray(mPositionHandle);

				GLES20.glVertexAttribPointer(mNormalHandle, NORMAL_DATA_SIZE_IN_ELEMENTS, GLES20.GL_FLOAT, false, STRIDE, POSITION_DATA_SIZE_IN_ELEMENTS
						* BYTES_PER_FLOAT);
				GLES20.glEnableVertexAttribArray(mNormalHandle);

				GLES20.glVertexAttribPointer(mColorHandle, COLOR_DATA_SIZE_IN_ELEMENTS, GLES20.GL_FLOAT, false, STRIDE,
						(POSITION_DATA_SIZE_IN_ELEMENTS + NORMAL_DATA_SIZE_IN_ELEMENTS) * BYTES_PER_FLOAT);
				GLES20.glEnableVertexAttribArray(mColorHandle);

				// Draw
				GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo[0]);
				GLES20.glDrawElements(GLES20.GL_TRIANGLE_STRIP, indexCount, GLES20.GL_UNSIGNED_SHORT, 0);

				GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
				GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
			}
		}

		void release() {
			if (vbo[0] > 0) {
				GLES20.glDeleteBuffers(vbo.length, vbo, 0);
				vbo[0] = 0;
			}

			if (ibo[0] > 0) {
				GLES20.glDeleteBuffers(ibo.length, ibo, 0);
				ibo[0] = 0;
			}
		}
	}

}
