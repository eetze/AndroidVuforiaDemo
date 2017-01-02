package com.eetze.firstvuforiademo;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Point;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

import com.vuforia.COORDINATE_SYSTEM_TYPE;
import com.vuforia.CameraDevice;
import com.vuforia.Device;
import com.vuforia.GLTextureUnit;
import com.vuforia.Matrix34F;
import com.vuforia.Mesh;
import com.vuforia.Renderer;
import com.vuforia.RenderingPrimitives;
import com.vuforia.State;
import com.vuforia.Tool;
import com.vuforia.TrackerManager;
import com.vuforia.VIDEO_BACKGROUND_REFLECTION;
import com.vuforia.VIEW;
import com.vuforia.Vec2F;
import com.vuforia.Vec2I;
import com.vuforia.Vec4I;
import com.vuforia.VideoBackgroundConfig;
import com.vuforia.VideoMode;
import com.vuforia.ViewList;

import com.eetze.firstvuforiademo.utils.VideoBackgroundShader;
import com.eetze.firstvuforiademo.utils.SampleUtils;

/**
 * 渲染器类
 */
public class SampleAppRenderer
{

    private static final String LOGTAG = "SampleAppRenderer";

    // Vuforia 绘制图元对象
    private RenderingPrimitives mRenderingPrimitives = null;
    // 渲染器控制器对象
    private SampleAppRendererControl mRenderingInterface = null;

    private Activity mActivity = null;

    // Vuforia 渲染器对象
    private Renderer mRenderer = null;

    private int currentView = VIEW.VIEW_SINGULAR;

    // 近平面
    private float mNearPlane = -1.0f;
    // 远平面
    private float mFarPlane = -1.0f;
    // Vuforia GL纹理单元
    private GLTextureUnit videoBackgroundTex = null;

    // AR模式渲染视频背景
    private int vbShaderProgramID = 0;
    private int vbTexSampler2DHandle = 0;
    private int vbVertexHandle = 0;
    private int vbTexCoordHandle = 0;
    private int vbProjectionMatrixHandle = 0;

    // 设备显示大小
    private int mScreenWidth = 0;
    private int mScreenHeight = 0;

    // 是否竖屏
    private boolean mIsPortrait = false;

    /**
     * 构造器
     * @param renderingInterface    渲染器控制器
     * @param activity              Activity
     * @param deviceMode            设备模式
     * @param stereo                立体模式
     * @param nearPlane             近平面
     * @param farPlane              远平面
     */
    public SampleAppRenderer(SampleAppRendererControl renderingInterface,
                             Activity activity,
                             int deviceMode,
                             boolean stereo,
                             float nearPlane,
                             float farPlane)
    {
        mActivity = activity;
        mRenderingInterface = renderingInterface;

        // Vuforia Renderer为单例模式，返回渲染器
        mRenderer = Renderer.getInstance();

        // 检查近平面与远平面参数合法性
        if(farPlane < nearPlane)
        {
            Log.i(LOGTAG, "Far plane should be greater than near plane");
            // 抛出非法参数异常
            throw new IllegalArgumentException();
        }
        // 设置近平面与远平面
        setNearFarPlanes(nearPlane, farPlane);

        // 检查设备模式合法性
        if(deviceMode != Device.MODE.MODE_AR
                && deviceMode != Device.MODE.MODE_VR)
        {
            Log.i(LOGTAG, "Device mode should be Device.MODE.MODE_AR or Device.MODE.MODE_VR");
            // 抛出非法参数异常
            throw new IllegalArgumentException();
        }

        // Vuforia Device 为单例模式，返回设备
        Device device = Device.getInstance();
        // 设置设备当前浏览器是否为活动状态
        device.setViewerActive(stereo);
        // 设置She被为AR或VR模式
        device.setMode(deviceMode);
    }

    /**
     * 当Surface被创建
     */
    public void onSurfaceCreated()
    {
        // 初始化渲染
        initRendering();
    }

    public void onConfigurationChanged(boolean isARActive)
    {
        updateActivityOrientation();
        storeScreenDimensions();

        if(isARActive)
            configureVideoBackground();

        mRenderingPrimitives = Device.getInstance().getRenderingPrimitives();
    }

    /**
     * 初始化渲染
     */
    void initRendering()
    {
        // wait to add
        vbShaderProgramID
                = SampleUtils.createProgramFromShaderSrc(
                VideoBackgroundShader.VB_VERTEX_SHADER,
                VideoBackgroundShader.VB_FRAGMENT_SHADER);

        // 视频背景的渲染模式
        if (vbShaderProgramID > 0)
        {
            // 根据ID选择需要激活的着色器程序
            GLES20.glUseProgram(vbShaderProgramID);

            // 获取着色器程序中的着色器
            vbTexSampler2DHandle
                    = GLES20.glGetUniformLocation(
                    vbShaderProgramID,
                    "texSampler2D");

            // 获取着色器程序中的投影矩阵句柄
            vbProjectionMatrixHandle
                    = GLES20.glGetUniformLocation(
                    vbShaderProgramID,
                    "projectionMatrix");

            // 获取着色器程序中的顶点坐标数据
            vbVertexHandle
                    = GLES20.glGetAttribLocation(
                    vbShaderProgramID,
                    "vertexPosition");

            // 获取着色器程序中的纹理坐标数据
            vbTexCoordHandle
                    = GLES20.glGetAttribLocation(
                    vbShaderProgramID,
                    "vertexTexCoord");

            // 获取着色器程序中的投影矩阵句柄，出现两遍？
            vbProjectionMatrixHandle
                    = GLES20.glGetUniformLocation(
                    vbShaderProgramID,
                    "projectionMatrix");

            // 获取着色器程序中的着色器，出现两遍？
            vbTexSampler2DHandle
                    = GLES20.glGetUniformLocation(
                    vbShaderProgramID,
                    "texSampler2D");

            // 停止使用的着色器程序
            GLES20.glUseProgram(0);
        }

        // 创建一个 Vuforia GL纹理单元
        videoBackgroundTex = new GLTextureUnit();
    }

    // Main rendering method
    // The method setup state for rendering, setup 3D transformations required for AR augmentation
    // and call any specific rendering method
    public void render()
    {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        State state;
        // Get our current state
        state = TrackerManager.getInstance().getStateUpdater().updateState();
        mRenderer.begin(state);

        // We must detect if background reflection is active and adjust the
        // culling direction.
        // If the reflection is active, this means the post matrix has been
        // reflected as well,
        // therefore standard counter clockwise face culling will result in
        // "inside out" models.
        if (Renderer.getInstance().getVideoBackgroundConfig().getReflection() == VIDEO_BACKGROUND_REFLECTION.VIDEO_BACKGROUND_REFLECTION_ON)
            GLES20.glFrontFace(GLES20.GL_CW);  // Front camera
        else
            GLES20.glFrontFace(GLES20.GL_CCW);   // Back camera

        // We get a list of views which depend on the mode we are working on, for mono we have
        // only one view, in stereo we have three: left, right and postprocess
        ViewList viewList = mRenderingPrimitives.getRenderingViews();

        // Cycle through the view list
        for (int v = 0; v < viewList.getNumViews(); v++)
        {
            // Get the view id
            int viewID = viewList.getView(v);

            Vec4I viewport;
            // Get the viewport for that specific view
            viewport = mRenderingPrimitives.getViewport(viewID);

            // Set viewport for current view
            GLES20.glViewport(viewport.getData()[0], viewport.getData()[1], viewport.getData()[2], viewport.getData()[3]);

            // Set scissor
            GLES20.glScissor(viewport.getData()[0], viewport.getData()[1], viewport.getData()[2], viewport.getData()[3]);

            // Get projection matrix for the current view. COORDINATE_SYSTEM_CAMERA used for AR and
            // COORDINATE_SYSTEM_WORLD for VR
            Matrix34F projMatrix = mRenderingPrimitives.getProjectionMatrix(viewID, COORDINATE_SYSTEM_TYPE.COORDINATE_SYSTEM_CAMERA);

            // Create GL matrix setting up the near and far planes
            float rawProjectionMatrixGL[] = Tool.convertPerspectiveProjection2GLMatrix(
                    projMatrix,
                    mNearPlane,
                    mFarPlane)
                    .getData();

            // Apply the appropriate eye adjustment to the raw projection matrix, and assign to the global variable
            float eyeAdjustmentGL[] = Tool.convert2GLMatrix(mRenderingPrimitives
                    .getEyeDisplayAdjustmentMatrix(viewID)).getData();

            float projectionMatrix[] = new float[16];
            // Apply the adjustment to the projection matrix
            Matrix.multiplyMM(projectionMatrix, 0, rawProjectionMatrixGL, 0, eyeAdjustmentGL, 0);

            currentView = viewID;

            // Call renderFrame from the app renderer class which implements SampleAppRendererControl
            // This will be called for MONO, LEFT and RIGHT views, POSTPROCESS will not render the
            // frame
            if(currentView != VIEW.VIEW_POSTPROCESS)
                mRenderingInterface.renderFrame(state, projectionMatrix);
        }

        mRenderer.end();
    }

    /**
     * 设置近平面与远平面
     * @param near  近平面
     * @param far   远平面
     */
    public void setNearFarPlanes(float near, float far)
    {
        mNearPlane = near;
        mFarPlane = far;
    }

    /**
     * 渲染视频背景
     */
    public void renderVideoBackground()
    {
        if(currentView == VIEW.VIEW_POSTPROCESS)
            return;

        int vbVideoTextureUnit = 0;
        // Bind the video bg texture and get the Texture ID from Vuforia
        videoBackgroundTex.setTextureUnit(vbVideoTextureUnit);
        if (!mRenderer.updateVideoBackgroundTexture(videoBackgroundTex))
        {
            Log.e(LOGTAG, "Unable to update video background texture");
            return;
        }

        float[] vbProjectionMatrix = Tool.convert2GLMatrix(
                mRenderingPrimitives.getVideoBackgroundProjectionMatrix(currentView, COORDINATE_SYSTEM_TYPE.COORDINATE_SYSTEM_CAMERA)).getData();

        // Apply the scene scale on video see-through eyewear, to scale the video background and augmentation
        // so that the display lines up with the real world
        // This should not be applied on optical see-through devices, as there is no video background,
        // and the calibration ensures that the augmentation matches the real world
        if (Device.getInstance().isViewerActive()) {
            float sceneScaleFactor = (float)getSceneScaleFactor();
            Matrix.scaleM(vbProjectionMatrix, 0, sceneScaleFactor, sceneScaleFactor, 1.0f);
        }

        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);

        Mesh vbMesh = mRenderingPrimitives.getVideoBackgroundMesh(currentView);
        // Load the shader and upload the vertex/texcoord/index data
        GLES20.glUseProgram(vbShaderProgramID);
        GLES20.glVertexAttribPointer(vbVertexHandle, 3, GLES20.GL_FLOAT, false, 0, vbMesh.getPositions().asFloatBuffer());
        GLES20.glVertexAttribPointer(vbTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, vbMesh.getUVs().asFloatBuffer());

        GLES20.glUniform1i(vbTexSampler2DHandle, vbVideoTextureUnit);

        // Render the video background with the custom shader
        // First, we enable the vertex arrays
        GLES20.glEnableVertexAttribArray(vbVertexHandle);
        GLES20.glEnableVertexAttribArray(vbTexCoordHandle);

        // Pass the projection matrix to OpenGL
        GLES20.glUniformMatrix4fv(vbProjectionMatrixHandle, 1, false, vbProjectionMatrix, 0);

        // 开始进行渲染
        // param 1: 类型
        // param 2: 数目
        // param 3: 第四个参数的类型
        // param 4: 绘制的时三角形的索引值
        GLES20.glDrawElements(GLES20.GL_TRIANGLES,
                vbMesh.getNumTriangles() * 3,
                GLES20.GL_UNSIGNED_SHORT,
                vbMesh.getTriangles().asShortBuffer());

        // Finally, we disable the vertex arrays
        GLES20.glDisableVertexAttribArray(vbVertexHandle);
        GLES20.glDisableVertexAttribArray(vbTexCoordHandle);

        SampleUtils.checkGLError("Rendering of the video background failed");
    }


    static final float VIRTUAL_FOV_Y_DEGS = 85.0f;
    static final float M_PI = 3.14159f;

    double getSceneScaleFactor()
    {
        // Get the y-dimension of the physical camera field of view
        Vec2F fovVector = CameraDevice.getInstance().getCameraCalibration().getFieldOfViewRads();
        float cameraFovYRads = fovVector.getData()[1];

        // Get the y-dimension of the virtual camera field of view
        float virtualFovYRads = VIRTUAL_FOV_Y_DEGS * M_PI / 180;

        // The scene-scale factor represents the proportion of the viewport that is filled by
        // the video background when projected onto the same plane.
        // In order to calculate this, let 'd' be the distance between the cameras and the plane.
        // The height of the projected image 'h' on this plane can then be calculated:
        //   tan(fov/2) = h/2d
        // which rearranges to:
        //   2d = h/tan(fov/2)
        // Since 'd' is the same for both cameras, we can combine the equations for the two cameras:
        //   hPhysical/tan(fovPhysical/2) = hVirtual/tan(fovVirtual/2)
        // Which rearranges to:
        //   hPhysical/hVirtual = tan(fovPhysical/2)/tan(fovVirtual/2)
        // ... which is the scene-scale factor
        return Math.tan(cameraFovYRads / 2) / Math.tan(virtualFovYRads / 2);
    }

    // Configures the video mode and sets offsets for the camera's image
    public void configureVideoBackground()
    {
        CameraDevice cameraDevice = CameraDevice.getInstance();
        VideoMode vm = cameraDevice.getVideoMode(CameraDevice.MODE.MODE_DEFAULT);

        VideoBackgroundConfig config = new VideoBackgroundConfig();
        config.setEnabled(true);
        config.setPosition(new Vec2I(0, 0));

        int xSize = 0, ySize = 0;
        // We keep the aspect ratio to keep the video correctly rendered. If it is portrait we
        // preserve the height and scale width and vice versa if it is landscape, we preserve
        // the width and we check if the selected values fill the screen, otherwise we invert
        // the selection
        if (mIsPortrait)
        {
            xSize = (int) (vm.getHeight() * (mScreenHeight / (float) vm
                    .getWidth()));
            ySize = mScreenHeight;

            if (xSize < mScreenWidth)
            {
                xSize = mScreenWidth;
                ySize = (int) (mScreenWidth * (vm.getWidth() / (float) vm
                        .getHeight()));
            }
        } else
        {
            xSize = mScreenWidth;
            ySize = (int) (vm.getHeight() * (mScreenWidth / (float) vm
                    .getWidth()));

            if (ySize < mScreenHeight)
            {
                xSize = (int) (mScreenHeight * (vm.getWidth() / (float) vm
                        .getHeight()));
                ySize = mScreenHeight;
            }
        }

        config.setSize(new Vec2I(xSize, ySize));

        Log.i(LOGTAG, "Configure Video Background : Video (" + vm.getWidth()
                + " , " + vm.getHeight() + "), Screen (" + mScreenWidth + " , "
                + mScreenHeight + "), mSize (" + xSize + " , " + ySize + ")");

        Renderer.getInstance().setVideoBackgroundConfig(config);

    }


    // Stores screen dimensions
    private void storeScreenDimensions()
    {
        // Query display dimensions:
        Point size = new Point();
        mActivity.getWindowManager().getDefaultDisplay().getRealSize(size);
        mScreenWidth = size.x;
        mScreenHeight = size.y;
    }


    // Stores the orientation depending on the current resources configuration
    private void updateActivityOrientation()
    {
        Configuration config = mActivity.getResources().getConfiguration();

        switch (config.orientation)
        {
            case Configuration.ORIENTATION_PORTRAIT:
                mIsPortrait = true;
                break;
            case Configuration.ORIENTATION_LANDSCAPE:
                mIsPortrait = false;
                break;
            case Configuration.ORIENTATION_UNDEFINED:
            default:
                break;
        }

        Log.i(LOGTAG, "Activity is in "
                + (mIsPortrait ? "PORTRAIT" : "LANDSCAPE"));
    }
}
