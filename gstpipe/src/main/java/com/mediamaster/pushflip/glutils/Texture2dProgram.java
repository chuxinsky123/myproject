/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mediamaster.pushflip.glutils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.view.MotionEvent;

import com.mediamaster.pushflip.Log;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;


/**
 * GL program and supporting functions for textured 2D shapes.
 */
public class Texture2dProgram {

	private static final boolean DEBUG = false;
    private static final String TAG = "Texture2dProgram";

	public enum ProgramType {
        TEXTURE_2D, TEXTURE_FILT3x3,
		TEXTURE_EXT, TEXTURE_EXT_BW, TEXTURE_EXT_NIGHT, TEXTURE_EXT_CHROMA_KEY,
        TEXTURE_EXT_SQUEEZE, TEXTURE_EXT_TWIRL, TEXTURE_EXT_TUNNEL, TEXTURE_EXT_BULGE,
        TEXTURE_EXT_DENT, TEXTURE_EXT_FISHEYE, TEXTURE_EXT_STRETCH, TEXTURE_EXT_MIRROR,
		TEXTURE_EXT_FILT3x3
    }

    // Simple vertex shader, used for all programs.
    private static final String VERTEX_SHADER =
		"uniform mat4 uMVPMatrix;\n" +
		"uniform mat4 uTexMatrix;\n" +
		"attribute vec4 aPosition;\n" +
		"attribute vec4 aTextureCoord;\n" +
		"varying vec2 vTextureCoord;\n" +
		"void main() {\n" +
		"    gl_Position = uMVPMatrix * aPosition;\n" +
		"    vTextureCoord = (uTexMatrix * aTextureCoord).xy;\n" +
		"}\n";

    // Simple fragment shader for use with "normal" 2D textures.
    private static final String FRAGMENT_SHADER_2D =
		"precision mediump float;\n" +
		"varying vec2 vTextureCoord;\n" +
		"uniform sampler2D sTexture;\n" +
		"void main() {\n" +
		"    gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
		"}\n";

    // Simple fragment shader for use with external 2D textures (e.g. what we get from
    // SurfaceTexture).
    private static final String FRAGMENT_SHADER_EXT =
		"#extension GL_OES_EGL_image_external : require\n" +
		"precision mediump float;\n" +
		"varying vec2 vTextureCoord;\n" +
		"uniform samplerExternalOES sTexture;\n" +
		"void main() {\n" +
		"    gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
		"}\n";

    // Fragment shader that converts color to black & white with a simple transformation.
    private static final String FRAGMENT_SHADER_EXT_BW =
		"#extension GL_OES_EGL_image_external : require\n" +
		"precision mediump float;\n" +
		"varying vec2 vTextureCoord;\n" +
		"uniform samplerExternalOES sTexture;\n" +
		"void main() {\n" +
		"    vec4 tc = texture2D(sTexture, vTextureCoord);\n" +
		"    float color = tc.r * 0.3 + tc.g * 0.59 + tc.b * 0.11;\n" +
		"    gl_FragColor = vec4(color, color, color, 1.0);\n" +
		"}\n";

    // Fragment shader that attempts to produce a high contrast image
    private static final String FRAGMENT_SHADER_EXT_NIGHT =
		"#extension GL_OES_EGL_image_external : require\n" +
		"precision mediump float;\n" +
		"varying vec2 vTextureCoord;\n" +
		"uniform samplerExternalOES sTexture;\n" +
		"void main() {\n" +
		"    vec4 tc = texture2D(sTexture, vTextureCoord);\n" +
		"    float color = ((tc.r * 0.3 + tc.g * 0.59 + tc.b * 0.11) - 0.5 * 1.5) + 0.8;\n" +
		"    gl_FragColor = vec4(color, color + 0.15, color, 1.0);\n" +
		"}\n";

    // Fragment shader that applies a Chroma Key effect, making green pixels transparent
    private static final String FRAGMENT_SHADER_EXT_CHROMA_KEY =
		"#extension GL_OES_EGL_image_external : require\n" +
		"precision mediump float;\n" +
		"varying vec2 vTextureCoord;\n" +
		"uniform samplerExternalOES sTexture;\n" +
		"void main() {\n" +
		"    vec4 tc = texture2D(sTexture, vTextureCoord);\n" +
		"    float color = ((tc.r * 0.3 + tc.g * 0.59 + tc.b * 0.11) - 0.5 * 1.5) + 0.8;\n" +
		"    if(tc.g > 0.6 && tc.b < 0.6 && tc.r < 0.6){ \n" +
		"        gl_FragColor = vec4(0, 0, 0, 0.0);\n" +
		"    }else{ \n" +
		"        gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
		"    }\n" +
		"}\n";


    private static final String FRAGMENT_SHADER_SQUEEZE =
		"#extension GL_OES_EGL_image_external : require\n" +
		"precision mediump float;\n" +
		"varying vec2 vTextureCoord;\n" +
		"uniform samplerExternalOES sTexture;\n" +
		"uniform vec2 uPosition;\n" +
		"void main() {\n" +
		"    vec2 texCoord = vTextureCoord.xy;\n" +
		"    vec2 normCoord = 2.0 * texCoord - 1.0;\n"+
		"    float r = length(normCoord); // to polar coords \n" +
		"    float phi = atan(normCoord.y + uPosition.y, normCoord.x + uPosition.x); // to polar coords \n"+
		"    r = pow(r, 1.0/1.8) * 0.8;\n"+  // Squeeze it
		"    normCoord.x = r * cos(phi); \n" +
		"    normCoord.y = r * sin(phi); \n" +
		"    texCoord = normCoord / 2.0 + 0.5;\n"+
		"    gl_FragColor = texture2D(sTexture, texCoord);\n"+
		"}\n";

    private static final String FRAGMENT_SHADER_TWIRL =
		"#extension GL_OES_EGL_image_external : require\n" +
		"precision mediump float;\n" +
		"varying vec2 vTextureCoord;\n" +
		"uniform samplerExternalOES sTexture;\n" +
		"uniform vec2 uPosition;\n" +
		"void main() {\n" +
		"    vec2 texCoord = vTextureCoord.xy;\n" +
		"    vec2 normCoord = 2.0 * texCoord - 1.0;\n"+
		"    float r = length(normCoord); // to polar coords \n" +
		"    float phi = atan(normCoord.y + uPosition.y, normCoord.x + uPosition.x); // to polar coords \n"+
		"    phi = phi + (1.0 - smoothstep(-0.5, 0.5, r)) * 4.0;\n"+ // Twirl it
		"    normCoord.x = r * cos(phi); \n" +
		"    normCoord.y = r * sin(phi); \n" +
		"    texCoord = normCoord / 2.0 + 0.5;\n"+
		"    gl_FragColor = texture2D(sTexture, texCoord);\n"+
		"}\n";

    private static final String FRAGMENT_SHADER_TUNNEL =
		"#extension GL_OES_EGL_image_external : require\n" +
		"precision mediump float;\n" +
		"varying vec2 vTextureCoord;\n" +
		"uniform samplerExternalOES sTexture;\n" +
		"uniform vec2 uPosition;\n" +
		"void main() {\n" +
		"    vec2 texCoord = vTextureCoord.xy;\n" +
		"    vec2 normCoord = 2.0 * texCoord - 1.0;\n"+
		"    float r = length(normCoord); // to polar coords \n" +
		"    float phi = atan(normCoord.y + uPosition.y, normCoord.x + uPosition.x); // to polar coords \n"+
		"    if (r > 0.5) r = 0.5;\n"+ // Tunnel
		"    normCoord.x = r * cos(phi); \n" +
		"    normCoord.y = r * sin(phi); \n" +
		"    texCoord = normCoord / 2.0 + 0.5;\n"+
		"    gl_FragColor = texture2D(sTexture, texCoord);\n"+
		"}\n";

    private static final String FRAGMENT_SHADER_BULGE =
		"#extension GL_OES_EGL_image_external : require\n" +
		"precision mediump float;\n" +
		"varying vec2 vTextureCoord;\n" +
		"uniform samplerExternalOES sTexture;\n" +
		"uniform vec2 uPosition;\n" +
		"void main() {\n" +
		"    vec2 texCoord = vTextureCoord.xy;\n" +
		"    vec2 normCoord = 2.0 * texCoord - 1.0;\n"+
		"    float r = length(normCoord); // to polar coords \n" +
		"    float phi = atan(normCoord.y + uPosition.y, normCoord.x + uPosition.x); // to polar coords \n"+
		"    r = r * smoothstep(-0.1, 0.5, r);\n"+ // Bulge
		"    normCoord.x = r * cos(phi); \n" +
		"    normCoord.y = r * sin(phi); \n" +
		"    texCoord = normCoord / 2.0 + 0.5;\n"+
		"    gl_FragColor = texture2D(sTexture, texCoord);\n"+
		"}\n";

    private static final String FRAGMENT_SHADER_DENT =
		"#extension GL_OES_EGL_image_external : require\n" +
		"precision mediump float;\n" +
		"varying vec2 vTextureCoord;\n" +
		"uniform samplerExternalOES sTexture;\n" +
		"uniform vec2 uPosition;\n" +
		"void main() {\n" +
		"    vec2 texCoord = vTextureCoord.xy;\n" +
		"    vec2 normCoord = 2.0 * texCoord - 1.0;\n"+
		"    float r = length(normCoord); // to polar coords \n" +
		"    float phi = atan(normCoord.y + uPosition.y, normCoord.x + uPosition.x); // to polar coords \n"+
		"    r = 2.0 * r - r * smoothstep(0.0, 0.7, r);\n"+ // Dent
		"    normCoord.x = r * cos(phi); \n" +
		"    normCoord.y = r * sin(phi); \n" +
		"    texCoord = normCoord / 2.0 + 0.5;\n"+
		"    gl_FragColor = texture2D(sTexture, texCoord);\n"+
		"}\n";

    private static final String FRAGMENT_SHADER_FISHEYE =
		"#extension GL_OES_EGL_image_external : require\n" +
		"precision mediump float;\n" +
		"varying vec2 vTextureCoord;\n" +
		"uniform samplerExternalOES sTexture;\n" +
		"uniform vec2 uPosition;\n" +
		"void main() {\n" +
		"    vec2 texCoord = vTextureCoord.xy;\n" +
		"    vec2 normCoord = 2.0 * texCoord - 1.0;\n"+
		"    float r = length(normCoord); // to polar coords \n" +
		"    float phi = atan(normCoord.y + uPosition.y, normCoord.x + uPosition.x); // to polar coords \n"+
		"    r = r * r / sqrt(2.0);\n"+ // Fisheye
		"    normCoord.x = r * cos(phi); \n" +
		"    normCoord.y = r * sin(phi); \n" +
		"    texCoord = normCoord / 2.0 + 0.5;\n"+
		"    gl_FragColor = texture2D(sTexture, texCoord);\n"+
		"}\n";

    private static final String FRAGMENT_SHADER_STRETCH =
		"#extension GL_OES_EGL_image_external : require\n" +
		"precision mediump float;\n" +
		"varying vec2 vTextureCoord;\n" +
		"uniform samplerExternalOES sTexture;\n" +
		"uniform vec2 uPosition;\n" +
		"void main() {\n" +
		"    vec2 texCoord = vTextureCoord.xy;\n" +
		"    vec2 normCoord = 2.0 * texCoord - 1.0;\n"+
		"    vec2 s = sign(normCoord + uPosition);\n"+
		"    normCoord = abs(normCoord);\n"+
		"    normCoord = 0.5 * normCoord + 0.5 * smoothstep(0.25, 0.5, normCoord) * normCoord;\n"+
		"    normCoord = s * normCoord;\n"+
		"    texCoord = normCoord / 2.0 + 0.5;\n"+
		"    gl_FragColor = texture2D(sTexture, texCoord);\n"+
		"}\n";

    private static final String FRAGMENT_SHADER_MIRROR =
		"#extension GL_OES_EGL_image_external : require\n" +
		"precision mediump float;\n" +
		"varying vec2 vTextureCoord;\n" +
		"uniform samplerExternalOES sTexture;\n" +
		"uniform vec2 uPosition;\n" +
		"void main() {\n" +
		"    vec2 texCoord = vTextureCoord.xy;\n" +
		"    vec2 normCoord = 2.0 * texCoord - 1.0;\n"+
		"    normCoord.x = normCoord.x * sign(normCoord.x + uPosition.x);\n"+
		"    texCoord = normCoord / 2.0 + 0.5;\n"+
		"    gl_FragColor = texture2D(sTexture, texCoord);\n"+
		"}\n";


    // Fragment shader with a convolution filter.  The upper-left half will be drawn normally,
    // the lower-right half will have the filter applied, and a thin red line will be drawn
    // at the border.
    //
    // This is not optimized for performance.  Some things that might make this faster:
    // - Remove the conditionals.  They're used to present a half & half view with a red
    //   stripe across the middle, but that's only useful for a demo.
    // - Unroll the loop.  Ideally the compiler does this for you when it's beneficial.
    // - Bake the filter kernel into the shader, instead of passing it through a uniform
    //   array.  That, combined with loop unrolling, should reduce memory accesses.
    public static final int KERNEL_SIZE = 9;
    private static final String FRAGMENT_SHADER_EXT_FILT3x3 =
		"#extension GL_OES_EGL_image_external : require\n" +
		"#define KERNEL_SIZE " + KERNEL_SIZE + "\n" +
		"precision highp float;\n" +
		"varying vec2 vTextureCoord;\n" +
		"uniform samplerExternalOES sTexture;\n" +
		"uniform float uKernel[KERNEL_SIZE];\n" +
		"uniform vec2 uTexOffset[KERNEL_SIZE];\n" +
		"uniform float uColorAdjust;\n" +
		"void main() {\n" +
		"    int i = 0;\n" +
		"    vec4 sum = vec4(0.0);\n" +
		"    for (i = 0; i < KERNEL_SIZE; i++) {\n"+
		"            vec4 texc = texture2D(sTexture, vTextureCoord + uTexOffset[i]);\n" +
		"            sum += texc * uKernel[i];\n" +
		"    }\n" +
		"    sum += uColorAdjust;\n" +
		"    gl_FragColor = sum;\n" +
		"}\n";

	private static final String FRAGMENT_SHADER_FILT3x3 =
		"#define KERNEL_SIZE " + KERNEL_SIZE + "\n" +
		"precision highp float;\n" +
		"varying vec2 vTextureCoord;\n" +
		"uniform sampler2D sTexture;\n" +
		"uniform float uKernel[KERNEL_SIZE];\n" +
		"uniform vec2 uTexOffset[KERNEL_SIZE];\n" +
		"uniform float uColorAdjust;\n" +
		"void main() {\n" +
		"    int i = 0;\n" +
		"    vec4 sum = vec4(0.0);\n" +
		"    for (i = 0; i < KERNEL_SIZE; i++) {\n"+
		"            vec4 texc = texture2D(sTexture, vTextureCoord + uTexOffset[i]);\n" +
		"            sum += texc * uKernel[i];\n" +
		"    }\n" +
		"    sum += uColorAdjust;\n" +
		"    gl_FragColor = sum;\n" +
		"}\n";

	private final ProgramType mProgramType;

    private float mTexWidth;
    private float mTexHeight;

    // Handles to the GL program and various components of it.
    private int mProgramHandle;
    private final int muMVPMatrixLoc;
    private final int muTexMatrixLoc;
    private int muKernelLoc;
    private int muTexOffsetLoc;
    private int muColorAdjustLoc;
    private final int maPositionLoc;
    private final int maTextureCoordLoc;
    private int muTouchPositionLoc;

    private int mTextureTarget;

    private final float[] mKernel = new float[KERNEL_SIZE];       // Inputs for convolution filter based shaders
    private final float[] mSummedTouchPosition = new float[2];    // Summed touch event delta
    private final float[] mLastTouchPosition = new float[2];      // Raw location of last touch event
    private float[] mTexOffset;
    private float mColorAdjust;

	private final float[] mModuleMatrix = new float[16];

	//default w
	public static  boolean w_landscape = true;

    /**
     * Prepares the program in the current EGL context.
     */
    public Texture2dProgram(final ProgramType programType) {
        mProgramType = programType;

		Matrix.setIdentityM(mModuleMatrix, 0);
		if (true == w_landscape)
		{
			Matrix.translateM( mModuleMatrix,  0,   -0.87f,  0.91f,  0  );   //横屏_平移矩阵
		}else {
			Matrix.translateM( mModuleMatrix,  0,   -0.76f,  0.93f,  0  );   //竖屏_平移矩阵
		}



		loadVertex_02();
		initShader();

        switch (programType) {
            case TEXTURE_2D:
                mTextureTarget = GLES20.GL_TEXTURE_2D;
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_2D);
                break;
			case TEXTURE_FILT3x3:
				mTextureTarget = GLES20.GL_TEXTURE_2D;
				mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_FILT3x3);
				break;
            case TEXTURE_EXT:
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_EXT);
                break;
            case TEXTURE_EXT_BW:
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_EXT_BW);
                break;
            case TEXTURE_EXT_NIGHT:
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_EXT_NIGHT);
                break;
            case TEXTURE_EXT_CHROMA_KEY:
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_EXT_CHROMA_KEY);
                break;
            case TEXTURE_EXT_SQUEEZE:
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_SQUEEZE);
                break;
            case TEXTURE_EXT_TWIRL:
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_TWIRL);
                break;
            case TEXTURE_EXT_TUNNEL:
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_TUNNEL);
                break;
            case TEXTURE_EXT_BULGE:
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_BULGE);
                break;
            case TEXTURE_EXT_FISHEYE:
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_FISHEYE);
                break;
            case TEXTURE_EXT_DENT:
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_DENT);
                break;
            case TEXTURE_EXT_MIRROR:
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_MIRROR);
                break;
            case TEXTURE_EXT_STRETCH:
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_STRETCH);
                break;
            case TEXTURE_EXT_FILT3x3:
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_EXT_FILT3x3);
                break;
            default:
                throw new RuntimeException("Unhandled type " + programType);
        }
        if (mProgramHandle == 0) {
            throw new RuntimeException("Unable to create program");
        }
        if (DEBUG) Log.d(TAG, "Created program " + mProgramHandle + " (" + programType + ")");

        // get locations of attributes and uniforms

        maPositionLoc = GLES20.glGetAttribLocation(mProgramHandle, "aPosition");
        GlUtil.checkLocation(maPositionLoc, "aPosition");
        maTextureCoordLoc = GLES20.glGetAttribLocation(mProgramHandle, "aTextureCoord");
        GlUtil.checkLocation(maTextureCoordLoc, "aTextureCoord");
        muMVPMatrixLoc = GLES20.glGetUniformLocation(mProgramHandle, "uMVPMatrix");
        GlUtil.checkLocation(muMVPMatrixLoc, "uMVPMatrix");
        muTexMatrixLoc = GLES20.glGetUniformLocation(mProgramHandle, "uTexMatrix");
        GlUtil.checkLocation(muTexMatrixLoc, "uTexMatrix");
        muKernelLoc = GLES20.glGetUniformLocation(mProgramHandle, "uKernel");
        if (muKernelLoc < 0) {
            // no kernel in this one
            muKernelLoc = -1;
            muTexOffsetLoc = -1;
            muColorAdjustLoc = -1;
        } else {
            // has kernel, must also have tex offset and color adj
            muTexOffsetLoc = GLES20.glGetUniformLocation(mProgramHandle, "uTexOffset");
            GlUtil.checkLocation(muTexOffsetLoc, "uTexOffset");
            muColorAdjustLoc = GLES20.glGetUniformLocation(mProgramHandle, "uColorAdjust");
            GlUtil.checkLocation(muColorAdjustLoc, "uColorAdjust");

            // initialize default values
            setKernel(new float[] {0f, 0f, 0f,  0f, 1f, 0f,  0f, 0f, 0f}, 0f);
            setTexSize(256, 256);
        }

        muTouchPositionLoc = GLES20.glGetUniformLocation(mProgramHandle, "uPosition");
        if(muTouchPositionLoc < 0){
            // Shader doesn't use position
            muTouchPositionLoc = -1;
        }else{
            // initialize default values
            //handleTouchEvent(new float[]{0f, 0f});
        }
    }

    /**
     * Releases the program.
     */
    public void release() {
        if (DEBUG) Log.d(TAG, "deleting program " + mProgramHandle);
        GLES20.glDeleteProgram(mProgramHandle);
        mProgramHandle = -1;
    }

    /**
     * Returns the program type.
     */
    public ProgramType getProgramType() {
        return mProgramType;
    }


    /**
     * Creates a texture object suitable for use with this program.
     * <p>
     * On exit, the texture will be bound.
     */
    public int createTextureObject() {
        final int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        GlUtil.checkGlError("glGenTextures");

        final int texId = textures[0];
        GLES20.glBindTexture(mTextureTarget, texId);
        GlUtil.checkGlError("glBindTexture " + texId);

        GLES20.glTexParameterf(mTextureTarget, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST);
        GLES20.glTexParameterf(mTextureTarget, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(mTextureTarget, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(mTextureTarget, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE);
        GlUtil.checkGlError("glTexParameter");

        return texId;
    }


    /**
     * Configures the effect offset
     *
     * This only has an effect for programs that
     * use positional effects like SQUEEZE and MIRROR
     */
    public void handleTouchEvent(final MotionEvent ev){
        if(ev.getAction() == MotionEvent.ACTION_MOVE){
            // A finger is dragging about
            if(mTexHeight != 0 && mTexWidth != 0){
                mSummedTouchPosition[0] += (2 * (ev.getX() - mLastTouchPosition[0])) / mTexWidth;
                mSummedTouchPosition[1] += (2 * (ev.getY() - mLastTouchPosition[1])) / -mTexHeight;
                mLastTouchPosition[0] = ev.getX();
                mLastTouchPosition[1] = ev.getY();
            }
        } else if(ev.getAction() == MotionEvent.ACTION_DOWN){
            // The primary finger has landed
            mLastTouchPosition[0] = ev.getX();
            mLastTouchPosition[1] = ev.getY();
        }
    }

    /**
     * Configures the convolution filter values.
     * This only has an effect for programs that use the
     * FRAGMENT_SHADER_EXT_FILT3x3 Fragment shader.
     *
     * @param values Normalized filter values; must be KERNEL_SIZE elements.
     */
    public void setKernel(final float[] values, final float colorAdj) {
        if (values.length != KERNEL_SIZE) {
            throw new IllegalArgumentException("Kernel size is " + values.length +
                    " vs. " + KERNEL_SIZE);
        }
        System.arraycopy(values, 0, mKernel, 0, KERNEL_SIZE);
        mColorAdjust = colorAdj;
        //Log.d(TAG, "filt kernel: " + Arrays.toString(mKernel) + ", adj=" + colorAdj);
    }

    /**
     * Sets the size of the texture.  This is used to find adjacent texels when filtering.
     */
    public void setTexSize(final int width, final int height) {
        mTexHeight = height;
        mTexWidth = width;
        final float rw = 1.0f / width;
        final float rh = 1.0f / height;

        // Don't need to create a new array here, but it's syntactically convenient.
        mTexOffset = new float[] {
                -rw, -rh,   0f, -rh,    rw, -rh,
                -rw, 0f,    0f, 0f,     rw, 0f,
                -rw, rh,    0f, rh,     rw, rh
        };
        //Log.d(TAG, "filt size: " + width + "x" + height + ": " + Arrays.toString(mTexOffset));
    }

	private void loadVertex() {
		//float size = 4
		this.vertex = ByteBuffer.allocateDirect(quadVertex.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
		this.vertex.put(quadVertex).position(0);
		//short size = 2
		this.index = ByteBuffer.allocateDirect(quadIndex.length * 2).order(ByteOrder.nativeOrder()).asShortBuffer();
		this.index.put(quadIndex).position(0);

		//loadTexture(R.drawable.texture);
		//initTexture("sdcard/my_logo.png");
	}

	//方式2
	private FloatBuffer mCubeBuffer;
	private FloatBuffer mTextureCubeBuffer;

	public void loadVertex_02() {
		float[] coord;
		if ( true == w_landscape )
		{
			coord = w_COORD1;
		}else {
			coord = h_COORD1;
		}
		w_landscape = true;
		float[] texture_coord = TEXTURE_COORD1;

		mCubeBuffer = ByteBuffer.allocateDirect(coord.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
		mCubeBuffer.put(coord).position(0);    //定位到0

		mTextureCubeBuffer = ByteBuffer.allocateDirect(texture_coord.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
		mTextureCubeBuffer.put(texture_coord).position(0);
	}

	private FloatBuffer vertex;
	private ShortBuffer index;
	private int mLoadedTextureId = -1;
	int attribPosition;
	int attribTexCoord;
	int mMpa1;

	private int mBaseMapLoc;
	private int mLightMapLoc;

	int fbuMMatrixHandle;

	static final float w_COORD1[] = {
			-0.06f*1.78f, -0.06f,
			0.06f*1.78f, -0.06f,
			-0.06f*1.78f, 0.06f,
			0.06f*1.78f, 0.06f,
	};

	static final float h_COORD1[] = {
			-0.06f*3.59f, -0.05f,
			0.06f*3.59f, -0.05f,
			-0.06f*3.59f, 0.05f,
			0.06f*3.59f, 0.05f,
	};

	static final float TEXTURE_COORD1[] = {
			0.0f, 1.0f,
			1.0f, 1.0f,
			0.0f, 0.0f,
			1.0f, 0.0f,
	};

	private float[] quadVertex = new float[] {
			-0.12f*1.78f, 0.12f, 0.0f,   // Position 0
			0, 1.0f,               // TexCoord 0

			-0.12f*1.78f, -0.12f, 0.0f,  // Position 1
			0, 0,                  // TexCoord 1

			0.12f*1.78f , -0.12f, 0.0f,  // Position 2
			1.0f, 0,               // TexCoord 2

			0.12f*1.78f, 0.12f, 0.0f,    // Position 3
			1.0f, 1.0f,            // TexCoord 3
	};

	private short[] quadIndex = new short[] {
			(short)(0), // Position 0
			(short)(1), // Position 1
			(short)(2), // Position 2
			(short)(2), // Position 2
			(short)(3), // Position 3
			(short)(0), // Position 0
	};
	public static int program;

	//init shader
	public void initShader() {
		String vertexSource = str_01;
		String fragmentSource = str_02;
		//Load the shaders and get a linked program
		program = loadProgram(vertexSource, fragmentSource);
		//Get the attribute locations
		attribPosition    = GLES20.glGetAttribLocation(program, "a_position");
		attribTexCoord    = GLES20.glGetAttribLocation(program, "a_texCoord");
		fbuMMatrixHandle  = GLES20.glGetUniformLocation(program, "u_MVPMatrix");
		mMpa1 = GLES20.glGetUniformLocation(program, "Map1");

//		mBaseMapLoc = GLES20.glGetUniformLocation(program, "s_baseMap");
//		mLightMapLoc = GLES20.glGetUniformLocation(program, "s_lightMap");
	}

	public int loadProgram(String vertexSource, String fragmentSource) {
		// Load the vertex shaders
		int vertexShader = loaderShader(GLES20.GL_VERTEX_SHADER, vertexSource);
		// Load the fragment shaders
		int fragmentShader = loaderShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);

		// Create the program object
		int program = GLES20.glCreateProgram();
		if (program == 0) {
			throw new RuntimeException("Error create program.");
		}
		GLES20.glAttachShader(program, vertexShader);
		GLES20.glAttachShader(program, fragmentShader);

		// Link the program
		GLES20.glLinkProgram(program);
		int[] linked = new int[1];
		// Check the link status
		GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);
		if (linked[0] == 0) {
			GLES20.glDeleteProgram(program);
			throw new RuntimeException("Error linking program: " +
					GLES20.glGetProgramInfoLog(program));
		}
		// Free up no longer needed shader resources
		GLES20.glDeleteShader(vertexShader);
		GLES20.glDeleteShader(fragmentShader);
		return program;
	}

	private int loaderShader(int type, String shaderCode) {
		int shader = GLES20.glCreateShader(type);
		GLES20.glShaderSource(shader, shaderCode);
		GLES20.glCompileShader(shader);
		return shader;
	}

	//加载纹理
	//public void initTexture(String res) {
	public void initTexture(Bitmap res) {
		if (null == res)  return;
		int [] textures = new int[1];
		GLES20.glGenTextures(1, textures, 0);
		mLoadedTextureId = textures[0];
		GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mLoadedTextureId);
		GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_NEAREST);
		GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
		GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_MIRRORED_REPEAT);
		GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_MIRRORED_REPEAT);
		//Bitmap bitmap = BitmapFactory.decodeFile(res);
		//Bitmap bitmap = BitmapFactory.decodeFile(res);


		GLUtils.texImage2D( GLES20.GL_TEXTURE_2D, 0, res, 0 );
		//GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
		res.recycle();
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

	}


    /**
     * Issues the draw call.  Does the full setup on every call.
     *
     * @param mvpMatrix The 4x4 projection matrix.
     * @param vertexBuffer Buffer with vertex position data.
     * @param firstVertex Index of first vertex to use in vertexBuffer.
     * @param vertexCount Number of vertices in vertexBuffer.
     * @param coordsPerVertex The number of coordinates per vertex (e.g. x,y is 2).
     * @param vertexStride Width, in bytes, of the position data for each vertex (often
     *        vertexCount * sizeof(float)).
     * @param texMatrix A 4x4 transformation matrix for texture coords.
     * @param texBuffer Buffer with vertex texture data.
     * @param texStride Width, in bytes, of the texture data for each vertex.
     */
    public void draw(final float[] mvpMatrix, final FloatBuffer vertexBuffer, final int firstVertex,
                     final int vertexCount, final int coordsPerVertex, final int vertexStride,
                     final float[] texMatrix, final FloatBuffer texBuffer, final int textureId, final int texStride) {
        GlUtil.checkGlError("draw start");

        // Select the program.
        GLES20.glUseProgram(mProgramHandle);
        GlUtil.checkGlError("glUseProgram");

        // Set the texture.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(mTextureTarget, textureId);
		GlUtil.checkGlError("glBindTexture");

		// Copy the model / view / projection matrix over.
        GLES20.glUniformMatrix4fv(muMVPMatrixLoc, 1, false, mvpMatrix, 0);
        GlUtil.checkGlError("glUniformMatrix4fv");

        // Copy the texture transformation matrix over.
        GLES20.glUniformMatrix4fv(muTexMatrixLoc, 1, false, texMatrix, 0);
        GlUtil.checkGlError("glUniformMatrix4fv");

        // Enable the "aPosition" vertex attribute.
        GLES20.glEnableVertexAttribArray(maPositionLoc);
        GlUtil.checkGlError("glEnableVertexAttribArray");

        // Connect vertexBuffer to "aPosition".
        GLES20.glVertexAttribPointer(maPositionLoc, coordsPerVertex,
                GLES20.GL_FLOAT, false, vertexStride, vertexBuffer);
        GlUtil.checkGlError("glVertexAttribPointer");

        // Enable the "aTextureCoord" vertex attribute.
        GLES20.glEnableVertexAttribArray(maTextureCoordLoc);
        GlUtil.checkGlError("glEnableVertexAttribArray");

        // Connect texBuffer to "aTextureCoord".
        GLES20.glVertexAttribPointer(maTextureCoordLoc, 2,
                GLES20.GL_FLOAT, false, texStride, texBuffer);
        GlUtil.checkGlError("glVertexAttribPointer");

        // Populate the convolution kernel, if present.
        if (muKernelLoc >= 0) {
            GLES20.glUniform1fv(muKernelLoc, KERNEL_SIZE, mKernel, 0);
            GLES20.glUniform2fv(muTexOffsetLoc, KERNEL_SIZE, mTexOffset, 0);
            GLES20.glUniform1f(muColorAdjustLoc, mColorAdjust);
        }

        // Populate touch position data, if present
        if (muTouchPositionLoc >= 0){
            GLES20.glUniform2fv(muTouchPositionLoc, 1, mSummedTouchPosition, 0);
        }
        // Draw the rect.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, firstVertex, vertexCount);
        GlUtil.checkGlError("glDrawArrays");

		if (-1 != mLoadedTextureId) {
			drwa_logo();
		}

        // Done -- disab
		// le vertex array, texture, and program.
        GLES20.glDisableVertexAttribArray(maPositionLoc);
        GLES20.glDisableVertexAttribArray(maTextureCoordLoc);
        GLES20.glBindTexture(mTextureTarget, 0);

		GLES20.glDisable(GLES20.GL_BLEND);

        GLES20.glUseProgram(0);
    }

	//draw texture
	private void drwa_logo()
	{
		//Draw rect02
		GLES20.glUseProgram(program);

		GLES20.glEnable(GLES20.GL_BLEND);
		GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

		GLES20.glUniform1i(mMpa1, 0);
		GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mLoadedTextureId);

		//GLES20.glUniform1i ( mLightMapLoc, 1 );

		GLES20.glUniformMatrix4fv(fbuMMatrixHandle, 1, false, mModuleMatrix, 0);

		mCubeBuffer.position(0);
		GLES20.glVertexAttribPointer(attribPosition,2,GLES20.GL_FLOAT, false, 0, mCubeBuffer);
		GLES20.glEnableVertexAttribArray(attribPosition);

		mTextureCubeBuffer.position(0);
		GLES20.glVertexAttribPointer(attribTexCoord,2,GLES20.GL_FLOAT, false, 0, mTextureCubeBuffer);
		GLES20.glEnableVertexAttribArray(attribTexCoord);

		GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
	}

	private String str_01 = "uniform mat4 u_MVPMatrix;"
			+ "attribute vec4 a_position;"
			+ "attribute vec2 a_texCoord;"
			+ "varying vec2 v_texCoord;"
			+ "void main()"
			+ "{"
			+ "gl_Position = u_MVPMatrix * a_position;"
			+ "v_texCoord = a_texCoord;"
			+ "}";

	private String str_02 = "precision lowp float;"
			+ "varying vec2 v_texCoord;"
			+ "uniform sampler2D Map1;"
			+ "void main()"
			+ "{"
			+ "gl_FragColor = texture2D(Map1, v_texCoord);"
			+ "}";
}
