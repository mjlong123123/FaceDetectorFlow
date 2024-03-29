package com.dragon.facedetectorflow.program

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLES20.GL_TEXTURE0
import android.opengl.GLES20.GL_TEXTURE_2D
import com.dragon.facedetectorflow.program.BasicProgram
import java.nio.FloatBuffer

class OesTextureProgram : BasicProgram(
    """
            attribute vec2 vPosition;
            attribute vec2 vInputTextureCoordinate;
            uniform mat4 mvpMatrix;
            varying vec2 vTextureCoordinate;
            void main(){
                gl_Position = mvpMatrix * vec4(vPosition,0.0,1.0);
                vTextureCoordinate = vInputTextureCoordinate; 
            }
        """,
    """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES inputTexture;
            varying vec2 vTextureCoordinate;
             void main(){
                if(vTextureCoordinate.x > 1.0 || vTextureCoordinate.x < 0.0 || vTextureCoordinate.y > 1.0 || vTextureCoordinate.y < 0.0) {
                    gl_FragColor = vec4(0.0,0.0,0.0,0.0);
                } else {
                    gl_FragColor = texture2D(inputTexture, vTextureCoordinate);
                }
            }
        """
) {
    private val vPosition by lazy { GLES20.glGetAttribLocation(programHandle, "vPosition") }
    private val vInputTextureCoordinate by lazy {
        GLES20.glGetAttribLocation(
            programHandle,
            "vInputTextureCoordinate"
        )
    }
    private val mvpMatrix by lazy { GLES20.glGetUniformLocation(programHandle, "mvpMatrix") }
    private val inputTexture by lazy { GLES20.glGetUniformLocation(programHandle, "inputTexture") }
    override fun draw(
        textureId: Int,
        positions: FloatBuffer,
        textureCoordinate: FloatBuffer,
        mvpMatrix: FloatArray
    ) {
        GLES20.glUseProgram(programHandle)
        GLES20.glEnableVertexAttribArray(vPosition)
        GLES20.glEnableVertexAttribArray(vInputTextureCoordinate)
        GLES20.glUniformMatrix4fv(this.mvpMatrix, 1, false, mvpMatrix, 0)
        GLES20.glActiveTexture(GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(inputTexture, 0)
        GLES20.glVertexAttribPointer(vPosition, 2, GLES20.GL_FLOAT, false, 0, positions)
        GLES20.glVertexAttribPointer(
            vInputTextureCoordinate,
            2,
            GLES20.GL_FLOAT,
            false,
            0,
            textureCoordinate
        )
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glBindTexture(GL_TEXTURE_2D, 0)
        GLES20.glDisableVertexAttribArray(vInputTextureCoordinate)
        GLES20.glDisableVertexAttribArray(vPosition)
        GLES20.glUseProgram(0)
    }
}