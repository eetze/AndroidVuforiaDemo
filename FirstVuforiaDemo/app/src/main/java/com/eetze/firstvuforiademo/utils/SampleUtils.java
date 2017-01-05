package com.eetze.firstvuforiademo.utils;

import android.opengl.GLES20;
import android.util.Log;

public class SampleUtils
{
    private static final String LOGTAG = "SampleUtils";

    /**
     * 初始化着色程序，着色器初始化主要分为5个步骤进行，详见代码
     * @param shaderType    着色程序类别 GL_VERTEX_SHADER or GL_FRAGMENT_SHADER
     * @param source        着色程序代码src
     * @return  着色器句柄
     */
    static int initShader(int shaderType, String source)
    {
        // [1] 创建一个新的着色程序，获取着色器句柄
        int shader = GLES20.glCreateShader(shaderType);

        // 非0表示着色器创建成功
        if (shader != 0)
        {
            // [2] 提供着色器源码
            GLES20.glShaderSource(shader, source);
            // [3] 变异着色器程序
            GLES20.glCompileShader(shader);

            // 保存便以结果的数组
            int[] glStatusVar = { GLES20.GL_FALSE };

            // [4] 查询编译结果
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS,
                    glStatusVar, 0);

            // 编译失败
            if (glStatusVar[0] == GLES20.GL_FALSE)
            {
                // [5] 打印Info日志
                Log.i(LOGTAG, "Could NOT compile shader " + shaderType + " : "
                    + GLES20.glGetShaderInfoLog(shader));
                // [6] 删除着色器
                GLES20.glDeleteShader(shader);
                shader = 0;
            }
        }

        // 返回着色器句柄
        return shader;
    }

    /**
     * 创建着色器程序对象
     * @param vertexShaderSrc       顶点着色器程序src
     * @param fragmentShaderSrc     片段着色器程序src
     * @return  0 失败; !0 成功
     */
    public static int createProgramFromShaderSrc(String vertexShaderSrc,
        String fragmentShaderSrc)
    {
        // 创建顶点着色器
        int vertShader = initShader(GLES20.GL_VERTEX_SHADER, vertexShaderSrc);
        // 创建片段着色器
        int fragShader = initShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderSrc);

        // 着色器创建失败返回0，是否需要释放创建成功的着色器？
        if (vertShader == 0 || fragShader == 0)
            return 0;

        // 创建一个着色程序对象
        int program = GLES20.glCreateProgram();

        if (program != 0)
        {
            // 将顶点着色器附加到着色程序对象上
            GLES20.glAttachShader(program, vertShader);
            checkGLError("glAttchShader(vert)");

            // 将片段着色器附加到着色程序对象上
            GLES20.glAttachShader(program, fragShader);
            checkGLError("glAttchShader(frag)");

            // 对着色程序对象进行链接操作，即生成最终的着色程序
            GLES20.glLinkProgram(program);

            int[] glStatusVar = { GLES20.GL_FALSE };
            // 查询链接是否成功
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS,
                    glStatusVar, 0);

            // 如果链接失败
            if (glStatusVar[0] == GLES20.GL_FALSE)
            {
                Log.i(LOGTAG, "Could NOT link program : "
                        + GLES20.glGetProgramInfoLog(program));
                // 删除着色程序对象
                GLES20.glDeleteProgram(program);
                program = 0;
            }
        }
        
        return program;
    }

    /**
     * 检查错误，错误来源为OpenGL ES 2.0
     * @param op    错误相关字符串
     */
    public static void checkGLError(String op)
    {
        for (int error = GLES20.glGetError();
             error != 0;
             error = GLES20.glGetError())
        {
            Log.i(LOGTAG, "After operation " + op + " got glError 0x"
                    + Integer.toHexString(error));
        }
    }
    
    /**
     * 屏幕坐标到相机坐标，相机图像会进行剪裁以适应不同的横纵比屏幕
     * 图形算法待深入研究
     * @param screenX
     * @param screenY
     * @param screenDX
     * @param screenDY
     * @param screenWidth
     * @param screenHeight
     * @param cameraWidth
     * @param cameraHeight
     * @param cameraX
     * @param cameraY
     * @param cameraDX
     * @param cameraDY
     * @param displayRotation
     * @param cameraRotation
     */
    public static void screenCoordToCameraCoord(int screenX, int screenY,
        int screenDX, int screenDY, int screenWidth, int screenHeight,
        int cameraWidth, int cameraHeight, int[] cameraX, int[] cameraY,
        int[] cameraDX, int[] cameraDY, int displayRotation, int cameraRotation)
    {
        float videoWidth, videoHeight;
        videoWidth = (float) cameraWidth;
        videoHeight = (float) cameraHeight;

        // Compute the angle by which the camera image should be rotated clockwise so that it is
        // shown correctly on the display given its current orientation.
        // 计算相机图像顺时针旋转的角度，使其在当前方向上显示正确。
        int correctedRotation = ((((displayRotation*90)-cameraRotation)+360)%360)/90;

        switch (correctedRotation)
        {
            case 0:
                break;

            case 1:
                int tmp = screenX;
                screenX = screenHeight - screenY;
                screenY = tmp;

                tmp = screenDX;
                screenDX = screenDY;
                screenDY = tmp;

                tmp = screenWidth;
                screenWidth = screenHeight;
                screenHeight = tmp;

                break;

            case 2:
                screenX = screenWidth - screenX;
                screenY = screenHeight - screenY;
                break;

            case 3:
                tmp = screenX;
                screenX = screenY;
                screenY = screenWidth - tmp;

                tmp = screenDX;
                screenDX = screenDY;
                screenDY = tmp;

                tmp = screenWidth;
                screenWidth = screenHeight;
                screenHeight = tmp;

                break;
        }
        
        float videoAspectRatio = videoHeight / videoWidth;
        float screenAspectRatio = (float) screenHeight / (float) screenWidth;
        
        float scaledUpX;
        float scaledUpY;
        float scaledUpVideoWidth;
        float scaledUpVideoHeight;
        
        if (videoAspectRatio < screenAspectRatio)
        {
            // 视频高度将适合屏幕高度
            scaledUpVideoWidth = (float) screenHeight / videoAspectRatio;
            scaledUpVideoHeight = screenHeight;
            scaledUpX = (float) screenX
                + ((scaledUpVideoWidth - (float) screenWidth) / 2.0f);
            scaledUpY = (float) screenY;
        }
        else
        {
            // 视频宽度将适合屏幕宽度
            scaledUpVideoHeight = (float) screenWidth * videoAspectRatio;
            scaledUpVideoWidth = screenWidth;
            scaledUpY = (float) screenY
                + ((scaledUpVideoHeight - (float) screenHeight) / 2.0f);
            scaledUpX = (float) screenX;
        }
        
        if (cameraX != null && cameraX.length > 0)
        {
            cameraX[0] = (int) ((scaledUpX / (float) scaledUpVideoWidth) * videoWidth);
        }
        
        if (cameraY != null && cameraY.length > 0)
        {
            cameraY[0] = (int) ((scaledUpY / (float) scaledUpVideoHeight) * videoHeight);
        }
        
        if (cameraDX != null && cameraDX.length > 0)
        {
            cameraDX[0] = (int) (((float) screenDX / (float) scaledUpVideoWidth) * videoWidth);
        }
        
        if (cameraDY != null && cameraDY.length > 0)
        {
            cameraDY[0] = (int) (((float) screenDY / (float) scaledUpVideoHeight) * videoHeight);
        }
    }

    /**
     * 获取正交矩阵，投影矩阵
     * 图形算法待深入研究
     * @param nLeft
     * @param nRight
     * @param nBottom
     * @param nTop
     * @param nNear
     * @param nFar
     * @return
     */
    public static float[] getOrthoMatrix(float nLeft, float nRight,
        float nBottom, float nTop, float nNear, float nFar)
    {
        float[] nProjMatrix = new float[16];
        
        int i;
        for (i = 0; i < 16; i++)
            nProjMatrix[i] = 0.0f;
        
        nProjMatrix[0] = 2.0f / (nRight - nLeft);
        nProjMatrix[5] = 2.0f / (nTop - nBottom);
        nProjMatrix[10] = 2.0f / (nNear - nFar);
        nProjMatrix[12] = -(nRight + nLeft) / (nRight - nLeft);
        nProjMatrix[13] = -(nTop + nBottom) / (nTop - nBottom);
        nProjMatrix[14] = (nFar + nNear) / (nFar - nNear);
        nProjMatrix[15] = 1.0f;
        
        return nProjMatrix;
    }
}
