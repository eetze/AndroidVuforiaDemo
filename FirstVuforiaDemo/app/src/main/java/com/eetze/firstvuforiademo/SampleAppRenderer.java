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
    private int vbShaderProgramID = 0;      // 着色程序对象句柄
    private int vbTexSampler2DHandle = 0;   // 使用哪一个采样器进行纹理采样变量句柄
    private int vbVertexHandle = 0;         // 顶点位置变量句柄
    private int vbTexCoordHandle = 0;       // 顶点纹理坐标变量句柄
    private int vbProjectionMatrixHandle = 0;   // 投影矩阵变量句柄

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

    /**
     * 当配置被改变
     * @param isARActive    是否为AR模式
     */
    public void onConfigurationChanged(boolean isARActive)
    {
        // 更新方向(纵向横向)信息
        updateActivityOrientation();
        // 获取屏幕尺寸
        storeScreenDimensions();

        // 如果是AR模式则对渲染器背景进行设置
        if(isARActive)
            configureVideoBackground();

        // 获取绘制图元对象
        mRenderingPrimitives = Device.getInstance().getRenderingPrimitives();
    }

    /**
     * 初始化渲染
     */
    void initRendering()
    {
        // 根据背景着色器src代码创建着色程序对象并获取其句柄
        vbShaderProgramID
                = SampleUtils.createProgramFromShaderSrc(
                VideoBackgroundShader.VB_VERTEX_SHADER,
                VideoBackgroundShader.VB_FRAGMENT_SHADER);

        // 视频背景的渲染模式
        if (vbShaderProgramID > 0)
        {
            // OpenGL渲染管道切换到着色器模式，并激活指定着色器程序
            GLES20.glUseProgram(vbShaderProgramID);

            // 使用哪一个采样器进行纹理采样
            vbTexSampler2DHandle
                    = GLES20.glGetUniformLocation(
                    vbShaderProgramID,
                    "texSampler2D");

            // 投影矩阵
            vbProjectionMatrixHandle
                    = GLES20.glGetUniformLocation(
                    vbShaderProgramID,
                    "projectionMatrix");

            // 顶点位置
            vbVertexHandle
                    = GLES20.glGetAttribLocation(
                    vbShaderProgramID,
                    "vertexPosition");

            // 顶点纹理坐标
            vbTexCoordHandle
                    = GLES20.glGetAttribLocation(
                    vbShaderProgramID,
                    "vertexTexCoord");

//            // 获取着色器程序中的投影矩阵句柄，出现两遍？
//            vbProjectionMatrixHandle
//                    = GLES20.glGetUniformLocation(
//                    vbShaderProgramID,
//                    "projectionMatrix");
//
//            // 获取着色器程序中的着色器，出现两遍？
//            vbTexSampler2DHandle
//                    = GLES20.glGetUniformLocation(
//                    vbShaderProgramID,
//                    "texSampler2D");

            // 停止使用的着色器程序
            GLES20.glUseProgram(0);
        }

        // 创建一个 Vuforia GL纹理单元
        videoBackgroundTex = new GLTextureUnit();
    }

    /**
     * 主要的绘制方法
     * 该方法为渲染设置状态，设置AR增强所需的3D转换，并调用特定的渲染方法
     */
    public void render()
    {
        // 清空渲染缓冲区（颜色缓冲区、深度缓冲区）
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // Vuforia 状态对象
        State state;

        // 获取当前状态
        state = TrackerManager.getInstance().getStateUpdater().updateState();

        // 开始渲染，标记渲染状态，并返回最新可用状态对象
        mRenderer.begin(state);

        // We must detect if background reflection is active and adjust the
        // culling direction.
        // If the reflection is active, this means the post matrix has been
        // reflected as well,
        // therefore standard counter clockwise face culling will result in
        // "inside out" models.
        if (Renderer.getInstance().getVideoBackgroundConfig().getReflection()
                == VIDEO_BACKGROUND_REFLECTION.VIDEO_BACKGROUND_REFLECTION_ON)
        {
            GLES20.glFrontFace(GLES20.GL_CW);   // 前置摄像头
        }
        else
        {
            GLES20.glFrontFace(GLES20.GL_CCW);  // 后置摄像头
        }

        // 返回可用于从这些图元绘制的视图集
        ViewList viewList = mRenderingPrimitives.getRenderingViews();

        // 通过循环查看视图列表
        for (int v = 0; v < viewList.getNumViews(); v++)
        {
            // 获取视图ID
            int viewID = viewList.getView(v);

            Vec4I viewport;

            // 获取图的视口
            viewport = mRenderingPrimitives.getViewport(viewID);

            // 设置视口到当前视图
            // 即设置将要绘制的2D物体的窗口的x、y、w、h
            GLES20.glViewport(viewport.getData()[0], viewport.getData()[1],
                    viewport.getData()[2], viewport.getData()[3]);

            // 设置剪切区域
            GLES20.glScissor(viewport.getData()[0], viewport.getData()[1],
                    viewport.getData()[2], viewport.getData()[3]);

            // 获取当前视图的投影矩阵
            // COORDINATE_SYSTEM_CAMERA  AR
            // COORDINATE_SYSTEM_WORLD   VR
            Matrix34F projMatrix = mRenderingPrimitives.getProjectionMatrix(viewID,
                    COORDINATE_SYSTEM_TYPE.COORDINATE_SYSTEM_CAMERA);

            // 创建GL矩阵，并设置近平面与远平面
            // convertPerspectiveProjection2GLMatrix() 将投影矩阵转换成GL矩阵
            float rawProjectionMatrixGL[] = Tool.convertPerspectiveProjection2GLMatrix(
                    projMatrix,
                    mNearPlane,
                    mFarPlane)
                    .getData();

            // 视口矩阵
            float eyeAdjustmentGL[] = Tool.convert2GLMatrix(mRenderingPrimitives
                    .getEyeDisplayAdjustmentMatrix(viewID)).getData();

            // 投影矩阵
            float projectionMatrix[] = new float[16];

            // 将调整应用于投影矩阵，合并矩阵
            Matrix.multiplyMM(projectionMatrix, 0,
                    rawProjectionMatrixGL, 0,
                    eyeAdjustmentGL, 0);

            // 当前视图ID
            currentView = viewID;

            // 跳转到 SampleAppRendererControl接口的实现中执行，进行帧渲染
            if(currentView != VIEW.VIEW_POSTPROCESS)
                mRenderingInterface.renderFrame(state, projectionMatrix);
        }

        // 结束渲染，取消渲染状态标记
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
        // 如果当前视图是待后处理的视图则return
        if(currentView == VIEW.VIEW_POSTPROCESS)
            return;

        int vbVideoTextureUnit = 0;

        // 绑定背景视频的纹理，并通过Vuforia获取纹理ID
        videoBackgroundTex.setTextureUnit(vbVideoTextureUnit);
        // 更新视频背景纹理
        if (!mRenderer.updateVideoBackgroundTexture(videoBackgroundTex))
        {
            Log.i(LOGTAG, "Unable to update video background texture");
            return;
        }

        // 获取当前视图的投影矩阵
        float[] vbProjectionMatrix = Tool.convert2GLMatrix(
                mRenderingPrimitives.getVideoBackgroundProjectionMatrix(currentView,
                        COORDINATE_SYSTEM_TYPE.COORDINATE_SYSTEM_CAMERA)).getData();

        // Apply the scene scale on video see-through eyewear, to scale the video background and augmentation
        // so that the display lines up with the real world
        // This should not be applied on optical see-through devices, as there is no video background,
        // and the calibration ensures that the augmentation matches the real world
        // 如果查看器处于活动状态，则返回true，否则为false。
        if (Device.getInstance().isViewerActive())
        {
            // 获取缩放因子
            float sceneScaleFactor = (float)getSceneScaleFactor();
            // 根据缩放因子对投影矩阵的X、Y轴进行缩放
            Matrix.scaleM(vbProjectionMatrix, 0, sceneScaleFactor,
                    sceneScaleFactor, 1.0f);
        }

        // 关闭更新深度缓冲区功能
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        // 关闭剔除操作效果
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        // 关闭剪裁测试功能
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);

        // 返回一个简单的网格，适合渲染视频背景纹理。
        Mesh vbMesh = mRenderingPrimitives.getVideoBackgroundMesh(currentView);

        // 使用选定的着色程序
        GLES20.glUseProgram(vbShaderProgramID);

        // 指定了渲染时索引为vbVertexHandle(顶点)的属性数组的数据格式和位置
        /* ===============================================================
         * index  指定要修改的顶点属性的索引值
         * size   指定每个顶点属性的组件数量。必须为1、2、3或者4。初始值为4。
         *       （如position是由3个（x,y,z）组成，而颜色是4个（r,g,b,a））
         * type   指定数组中每个组件的数据类型。可用的符号常量有GL_BYTE, GL_UNSIGNED_BYTE,
         *        GL_SHORT,GL_UNSIGNED_SHORT, GL_FIXED, 和 GL_FLOAT，初始值为GL_FLOAT。
         * normalized  指定当被访问时，固定点数据值是否应该被归一化（GL_TRUE）或者直接转换
         *             为固定点值（GL_FALSE）。
         * stride  指定连续顶点属性之间的偏移量。如果为0，那么顶点属性会被理解为：
         *         它们是紧密排列在一起的。初始值为0。
         * pointer 指定第一个组件在数组的第一个顶点属性中的偏移量。
         *         该数组与GL_ARRAY_BUFFER绑定，储存于缓冲区中。初始值为0；
         * =============================================================== */
        GLES20.glVertexAttribPointer(vbVertexHandle, 3, GLES20.GL_FLOAT,
                false, 0, vbMesh.getPositions().asFloatBuffer());

        // 指定了渲染时索引值为vbTexCoordHandle(纹理)的属性数组的数据格式和位置
        GLES20.glVertexAttribPointer(vbTexCoordHandle, 2, GLES20.GL_FLOAT,
                false, 0, vbMesh.getUVs().asFloatBuffer());

        // 根据之前设置的采样器进行纹理采样变量句柄 进行常亮装在
        GLES20.glUniform1i(vbTexSampler2DHandle, vbVideoTextureUnit);

        // 使用前面指定的两个数组数据
        GLES20.glEnableVertexAttribArray(vbVertexHandle);
        GLES20.glEnableVertexAttribArray(vbTexCoordHandle);

        // 根据之前设置的投影矩阵变量句柄 进行投影矩阵设置
        GLES20.glUniformMatrix4fv(vbProjectionMatrixHandle, 1, false,
                vbProjectionMatrix, 0);

        // 开始进行渲染
        // param 1: 类型
        // param 2: 数目
        // param 3: 第四个参数的类型
        // param 4: 绘制的时三角形的索引值
        GLES20.glDrawElements(GLES20.GL_TRIANGLES,
                vbMesh.getNumTriangles() * 3,
                GLES20.GL_UNSIGNED_SHORT,
                vbMesh.getTriangles().asShortBuffer());

        // 最后，禁用前面指定的两个数组数据
        GLES20.glDisableVertexAttribArray(vbVertexHandle);
        GLES20.glDisableVertexAttribArray(vbTexCoordHandle);

        // 检查OpenGL ES 2.0 是否有错，出现错误信息则打印相应log信息
        SampleUtils.checkGLError("Rendering of the video background failed");
    }

    // 虚拟摄像头Y轴视角度数
    static final float VIRTUAL_FOV_Y_DEGS = 85.0f;
    // π值
    static final float M_PI = 3.14159f;

    /**
     * 获取场景缩放因子
     * @return
     */
    double getSceneScaleFactor()
    {
        // 获取物理摄像机视野的y维度
        Vec2F fovVector = CameraDevice.getInstance()
                .getCameraCalibration().getFieldOfViewRads();
        float cameraFovYRads = fovVector.getData()[1];

        // 获取虚拟摄像机视野的y维度
        float virtualFovYRads = VIRTUAL_FOV_Y_DEGS * M_PI / 180;

        // The scene-scale factor represents the proportion of the viewport that is filled by
        // the video background when projected onto the same plane.
        // In order to calculate this, let 'd' be the distance between the cameras and the plane.
        // The height of the projected image 'h' on this plane can then be calculated:
        // d为镜头到面的距离，面高为h，则1/2视角（fov/2）的正切为  1/2 * h / d，即h/2d
        //   tan(fov/2) = h/2d
        // 转换得到：
        //   2d = h/tan(fov/2)
        // Since 'd' is the same for both cameras, we can combine the equations for the two cameras:
        // 物理摄像头与虚拟摄像头需要成像的面到摄像头的距离都为d，可以得到如下公式
        //   hPhysical/tan(fovPhysical/2) = hVirtual/tan(fovVirtual/2)
        // 转换得到:
        //   hPhysical/hVirtual = tan(fovPhysical/2)/tan(fovVirtual/2)
        // 物理成像面 / 虚拟成像面 就是所需的缩放因子，所以return如下表达式
        return Math.tan(cameraFovYRads / 2) / Math.tan(virtualFovYRads / 2);
    }

    /**
     * 设置渲染器的背景大小，配置视频模式和设置相机的图像偏移
     */
    public void configureVideoBackground()
    {
        // Vuforia 手机摄像头对象
        CameraDevice cameraDevice = CameraDevice.getInstance();

        // Vuforia 获取默认视频模式对象
        VideoMode vm = cameraDevice.getVideoMode(CameraDevice.MODE.MODE_DEFAULT);

        // Vuforia 视频背景配置选项
        VideoBackgroundConfig config = new VideoBackgroundConfig();

        // 启用视频背景渲染
        config.setEnabled(true);

        // 设置视频显示时的相对位置
        config.setPosition(new Vec2I(0, 0));

        int xSize = 0, ySize = 0;

        // 通过保持横纵比来保持视频的正常渲染。
        // 如果为纵向，则保持Activity的高度不变，然后计算获得宽度
        // 反之为横屏，则保持Activity的宽度不变
        if (mIsPortrait)    // 纵向
        {
            // 保持高度不变，通过视频模式对象中的宽高比求得宽度
            xSize = (int) (vm.getHeight() * (mScreenHeight / (float) vm.getWidth()));
            // 高度保持不变
            ySize = mScreenHeight;

            // 如果计算得到的宽度小于实际Activity的宽度
            if (xSize < mScreenWidth)
            {
                // 此时以宽度为准保持不变，求高度
                xSize = mScreenWidth;
                ySize = (int) (mScreenWidth * (vm.getWidth() / (float) vm.getHeight()));
            }
        }
        else    // 横向
        {
            // 同上亦相反
            xSize = mScreenWidth;
            ySize = (int) (vm.getHeight() * (mScreenWidth / (float) vm.getWidth()));

            if (ySize < mScreenHeight)
            {
                xSize = (int) (mScreenHeight * (vm.getWidth() / (float) vm.getHeight()));
                ySize = mScreenHeight;
            }
        }

        // 将计算得到的X、Y设置到视频背景设置对象中
        config.setSize(new Vec2I(xSize, ySize));

        Log.i(LOGTAG, "Configure Video Background : Video (" + vm.getWidth()
                + " , " + vm.getHeight() + "), Screen (" + mScreenWidth + " , "
                + mScreenHeight + "), mSize (" + xSize + " , " + ySize + ")");

        // 对渲染器背景进行设置
        Renderer.getInstance().setVideoBackgroundConfig(config);
    }

    /**
     * 获取屏幕尺寸
     * 保存在mScreenWidth、mScreenHeight种
     */
    private void storeScreenDimensions()
    {
        // 获取Activity显示尺寸
        Point size = new Point();
        mActivity.getWindowManager().getDefaultDisplay().getRealSize(size);
        mScreenWidth = size.x;
        mScreenHeight = size.y;
    }

    /**
     * 根据资源判断Activity方向（纵向or横向）
     * 主要表现为修改mIsPortrait取值，true 纵向；false 横向。
     */
    private void updateActivityOrientation()
    {
        // 获取布局对象
        Configuration config = mActivity.getResources().getConfiguration();
        // 判断方向
        switch (config.orientation)
        {
            case Configuration.ORIENTATION_PORTRAIT:    // 纵向
                mIsPortrait = true;
                break;
            case Configuration.ORIENTATION_LANDSCAPE:   // 横向
                mIsPortrait = false;
                break;
            case Configuration.ORIENTATION_UNDEFINED:   // 未声明
            default:
                break;
        }

        Log.i(LOGTAG, "Activity is in "
                + (mIsPortrait ? "PORTRAIT" : "LANDSCAPE"));
    }
}
