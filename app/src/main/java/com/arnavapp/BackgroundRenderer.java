package com.arnavapp;

import android.content.Context;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import com.google.ar.core.Coordinates2d;
import com.google.ar.core.Frame;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class BackgroundRenderer {
    private static final String TAG = BackgroundRenderer.class.getSimpleName();

    private static final int COORDS_PER_VERTEX = 2;
    private static final int TEXCOORDS_PER_VERTEX = 2;
    private static final int FLOAT_SIZE = 4;

    private FloatBuffer quadCoords;
    private FloatBuffer quadTexCoords;

    private int quadProgram;
    private int quadPositionParam;
    private int quadTexCoordParam;
    private int textureUniformParam;
    private int textureId = -1;

    private static final float[] QUAD_COORDS = new float[]{
            -1.0f, -1.0f,
            -1.0f, +1.0f,
            +1.0f, -1.0f,
            +1.0f, +1.0f,
    };

    private static final String VERTEX_SHADER_CODE =
            "attribute vec4 a_Position;" +
                    "attribute vec2 a_TexCoord;" +
                    "varying vec2 v_TexCoord;" +
                    "void main() {" +
                    "   gl_Position = a_Position;" +
                    "   v_TexCoord = a_TexCoord;" +
                    "}";

    private static final String FRAGMENT_SHADER_CODE =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;" +
                    "varying vec2 v_TexCoord;" +
                    "uniform samplerExternalOES sTexture;" +
                    "void main() {" +
                    "    gl_FragColor = texture2D(sTexture, v_TexCoord);" +
                    "}";

    public void createOnGlThread(Context context) {
        // Generate texture
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        textureId = textures[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        // Buffers
        ByteBuffer bbCoords = ByteBuffer.allocateDirect(QUAD_COORDS.length * FLOAT_SIZE);
        bbCoords.order(ByteOrder.nativeOrder());
        quadCoords = bbCoords.asFloatBuffer();
        quadCoords.put(QUAD_COORDS);
        quadCoords.position(0);

        ByteBuffer bbTexCoords = ByteBuffer.allocateDirect(4 * TEXCOORDS_PER_VERTEX * FLOAT_SIZE);
        bbTexCoords.order(ByteOrder.nativeOrder());
        quadTexCoords = bbTexCoords.asFloatBuffer();

        // Shaders
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_CODE);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_CODE);

        quadProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(quadProgram, vertexShader);
        GLES20.glAttachShader(quadProgram, fragmentShader);
        GLES20.glLinkProgram(quadProgram);

        quadPositionParam = GLES20.glGetAttribLocation(quadProgram, "a_Position");
        quadTexCoordParam = GLES20.glGetAttribLocation(quadProgram, "a_TexCoord");
        textureUniformParam = GLES20.glGetUniformLocation(quadProgram, "sTexture");
    }

    public int getTextureId() {
        return textureId;
    }

    public void draw(Frame frame) {
        if (frame == null) return;
        if (!frame.hasDisplayGeometryChanged()) {
            // Usually we'd check if display geometry changed to update UV coords.
            // But we can just update them every frame for simplicity.
        }
        
        // Update UV coords
        FloatBuffer uvCoords = quadTexCoords;
        float[] baseUv = new float[]{
                0.0f, 1.0f,
                0.0f, 0.0f,
                1.0f, 1.0f,
                1.0f, 0.0f
        };
        uvCoords.position(0);
        uvCoords.put(baseUv);
        uvCoords.position(0);

        frame.transformCoordinates2d(
                Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
                quadCoords,
                Coordinates2d.TEXTURE_NORMALIZED,
                uvCoords);

        // Draw
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthMask(false);

        GLES20.glUseProgram(quadProgram);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        GLES20.glUniform1i(textureUniformParam, 0);

        GLES20.glEnableVertexAttribArray(quadPositionParam);
        GLES20.glVertexAttribPointer(
                quadPositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, quadCoords);

        GLES20.glEnableVertexAttribArray(quadTexCoordParam);
        GLES20.glVertexAttribPointer(
                quadTexCoordParam, TEXCOORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, quadTexCoords);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glDisableVertexAttribArray(quadPositionParam);
        GLES20.glDisableVertexAttribArray(quadTexCoordParam);

        GLES20.glDepthMask(true);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    }

    private int loadShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        return shader;
    }
}
