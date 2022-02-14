package com.dragon.facedetectorflow.program

import android.opengl.GLES20
import android.opengl.GLES20.GL_TEXTURE1
import android.opengl.GLES20.GL_TEXTURE_2D
import com.dragon.facedetectorflow.utils.OpenGlUtils
import java.nio.FloatBuffer

class FaceDistortionProgram() : BasicProgram(
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
            precision mediump float;
            uniform sampler2D inputTexture;
            uniform vec2 originalPoint;
            uniform vec2 targetPoint;
            uniform int faceDistortion;
            uniform float intensity;
            varying vec2 vTextureCoordinate;
            
            vec2 narrowFun(vec2 curCoord,vec2 circleCenter,float radius,float intensity,float curve)
            {
                float currentDistance = distance(curCoord,circleCenter);
                if(currentDistance <= radius)
                {
                    float weight = currentDistance/radius;
                    weight = 1.0-intensity*(1.0-pow(weight,curve));//默认curve 为2 ,当 curve 越大时, 会放大得越大的,
                    weight = clamp(weight,0.0,1.0);
                    curCoord = circleCenter+(curCoord-circleCenter)*weight;
                }
                return curCoord;
            }
            
            vec2 stretchFun(vec2 textureCoord, vec2 originPosition, vec2 targetPosition, float radius,float curve)
            {
                vec2 offset = vec2(0.0);
                vec2 result = vec2(0.0);
            
                vec2 direction = targetPosition - originPosition;
            
            
                float infect = distance(textureCoord, originPosition)/radius;
            
                infect = pow(infect,curve);
                infect = 1.0-infect;
                infect = clamp(infect,0.0,1.0);
                offset = direction * infect;
                result = textureCoord - offset;
            
                return result;
            }

             void main(){
                if(vTextureCoordinate.x > 1.0 || vTextureCoordinate.x < 0.0 || vTextureCoordinate.y > 1.0 || vTextureCoordinate.y < 0.0) {
                    gl_FragColor = vec4(0.0,0.0,0.0,0.0);
                } else {
                    if(faceDistortion == 1){
                        vec2 textCood = stretchFun(vTextureCoordinate,originalPoint,targetPoint, 0.19f, 2.0f);
                        gl_FragColor = texture2D(inputTexture, textCood);
                    }else{
                        vec2 textCood = narrowFun(vTextureCoordinate,originalPoint, 0.19f, intensity, 2.0f);
                        gl_FragColor = texture2D(inputTexture, textCood);
                    }
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
    private val originalPointLocation by lazy { GLES20.glGetUniformLocation(programHandle, "originalPoint") }
    private val targetPointLocation by lazy { GLES20.glGetUniformLocation(programHandle, "targetPoint") }
    private val intensityLocation by lazy { GLES20.glGetUniformLocation(programHandle, "intensity") }
    private val faceDistortionLocation by lazy { GLES20.glGetUniformLocation(programHandle, "faceDistortion") }

    var rowCount = 2
    var colCount = 2

    var intensity = 1.0f
    var faceDistortion = 1
    val originalPoint = OpenGlUtils.BufferUtils.generateFloatBuffer(2)
    val targetPoint = OpenGlUtils.BufferUtils.generateFloatBuffer(2)

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
        GLES20.glUniform2fv(originalPointLocation, 1, originalPoint)
        GLES20.glUniform2fv(targetPointLocation, 1, targetPoint)
        GLES20.glUniform1i(faceDistortionLocation, faceDistortion)
        GLES20.glUniform1f(intensityLocation, intensity)
        GLES20.glActiveTexture(GL_TEXTURE1)
        GLES20.glBindTexture(GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(inputTexture, 1)
        GLES20.glVertexAttribPointer(vPosition, 2, GLES20.GL_FLOAT, false, 0, positions)
        GLES20.glVertexAttribPointer(
            vInputTextureCoordinate,
            2,
            GLES20.GL_FLOAT,
            false,
            0,
            textureCoordinate
        )
        for (row in 0 until rowCount) {
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, row * colCount * 2, colCount * 2)
        }
        GLES20.glBindTexture(GL_TEXTURE_2D, 0)
        GLES20.glDisableVertexAttribArray(vInputTextureCoordinate)
        GLES20.glDisableVertexAttribArray(vPosition)
        GLES20.glUseProgram(0)
    }
}