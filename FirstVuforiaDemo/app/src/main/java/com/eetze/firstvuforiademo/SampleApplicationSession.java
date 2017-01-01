package com.eetze.firstvuforiademo;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.WindowManager;

// 摄像头校准
import com.vuforia.CameraCalibration;
import com.vuforia.CameraDevice;
import com.vuforia.Device;
import com.vuforia.Matrix44F;
//一个4*4的矩阵，用于记录坐标变，是计算机图形学的基础
//一个空间的点可以用一个1*4的行向量表示（x,y,z,1）,最后一个1表示的是比例，若为2，表示坐标为（2x,2y,2z）,
// 传送门http://379910987.blog.163.com/blog/static/335237972010111010363383/
//http://www.cnblogs.com/TianFang/p/3920734.html
//http://wenku.baidu.com/link?url=Obq5QfYY5LDgBNWz1dC1IICLl05kEX44lwJgW6CGvdTHv75qHJla40upMduy_nTxxo0g5Eb7Yxbe-qLbBCk2w4Gn2JapdtrbHWKDgpZMB_y
import com.vuforia.Renderer;
import com.vuforia.State;
import com.vuforia.Tool;
import com.vuforia.Vec2I;// vec为容器，2表示2D，I表示int,一种数据结构，后面的vec4f类似
import com.vuforia.VideoBackgroundConfig;
import com.vuforia.VideoMode;
import com.vuforia.Vuforia;
import com.vuforia.Vuforia.UpdateCallbackInterface;

import com.eetze.firstvuforiademo.R;

// Vuforia.UpdateCallbackInterface   Vuforia_onUpdate()   SDK完成后调用
public class SampleApplicationSession implements UpdateCallbackInterface
{
    
    private static final String LOGTAG = "SampleAppSession";
    
    // Reference to the current activity
    // 当前Activity的引用
    private Activity mActivity;

    // 示例应用管理接口
    private SampleApplicationControl mSessionControl;
    
    // Flags
    // 是否启动
    private boolean mStarted = false;
    // 相机是否启用
    private boolean mCameraRunning = false;

    // The async tasks to initialize the Vuforia SDK:
    // async tasks为异步任务，用于处理UI线程外的工作，这里用于进行vuforia的初始化
    // 初始化Vuforia的异步任务
    private InitVuforiaTask mInitVuforiaTask;
    // 加载跟踪器数据的异步任务
    private LoadTrackerTask mLoadTrackerTask;
    
    // An object used for synchronizing Vuforia initialization, dataset loading
    // and the Android onDestroy() life cycle event. If the application is
    // destroyed while a data set is still being loaded, then we wait for the
    // loading operation to finish before shutting down Vuforia:
    private Object mShutdownLock = new Object();
    
    // Vuforia initialization flags:
    // Vuforia 初始化标识符
    // 0 未初始化
    // 1 GL_20
    private int mVuforiaFlags = 0;
    
    // Holds the camera configuration to use upon resuming
    // CAMERA_DIRECTION摄像头的设备，CAMERA_DIRECTION_DEFAULT默认摄像头
    // CAMERA_DIRECTION_FRONT前摄像头，CAMERA_DIRECTION_BACK后摄像头
    private int mCamera = CameraDevice.CAMERA_DIRECTION.CAMERA_DIRECTION_DEFAULT;
    

    public SampleApplicationSession(SampleApplicationControl sessionControl)
    {
        // most important构造函数
        // SampleApplicationControl为接口
        // 参数传进来都是一个activiy对象，这些对象都继承了control接口
        // 故这些对象可以上转型赋值给一个control接口
        // mSessionContol就是用来调用activity实现SampleApplicationControl里面的函数，起到一个中介的作用
        // 把不同的actvity赋值给mSessionControl，就能调用各自不同的实现方法
        mSessionControl = sessionControl;
    }
    
    
    // Initializes Vuforia and sets up preferences.
    /**
     * 初始化Vuforia 及 设置屏幕方向
     * @param activity
     * @param screenOrientation 屏幕方向
     */
    public void initAR(Activity activity, int screenOrientation)
    {
        // Vuforia处理异常对象
        SampleApplicationException vuforiaException = null;

        mActivity = activity;

        // 设置全屏转向
        // Build.VERSION.SDK_INT 当前系统版本
        /*
           Build.VERSION_CODES   编译时SDK版本
            ECLAIR_MR1        January 2010:   Android 2.1
            FROYO             June 2010:      Android 2.2
            GINGERBREAD       November 2010:  Android 2.3
            GINGERBREAD_MR1   February 2011:  Android 2.3.3.
            HONEYCOMB         February 2011:  Android 3.0.
            HONEYCOMB_MR1     May 2011:       Android 3.1.
            HONEYCOMB_MR2     June 2011:      Android 3.2.
            ICE_CREAM_SANDWICH                Android 4.0.
        */
        // ActivityInfo.SCREEN_ORIENTATION_SENSOR       由物理感应器决定显示方向
        // ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR  根据重力变换朝向，全屏旋转
        if ((screenOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR)
            && (Build.VERSION.SDK_INT > Build.VERSION_CODES.FROYO))
            screenOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR;

        // Use an OrientationChangeListener here to capture all orientation changes.  Android
        // will not send an Activity.onConfigurationChanged() callback on a 180 degree rotation,
        // ie: Left Landscape to Right Landscape.  Vuforia needs to react to this change and the
        // SampleApplicationSession needs to update the Projection Matrix.
        // 使用 OrientationChangeListener 在此处捕获方向变化
        // 当180度旋转时，Android将不再发送Activity.onConfigurationChanged()回调
        // 也就是向左横屏到向右横屏时，Vuforia需要对此操作做出反应
        // 并且SampleApplicationSession需要更新投影矩阵
        // OrientationEventListener 方向事件监听器
        OrientationEventListener orientationEventListener = new OrientationEventListener(mActivity) {
            @Override
            public void onOrientationChanged(int i) {
                int activityRotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
                if(mLastRotation != activityRotation)
                {
                    mLastRotation = activityRotation;
                }
            }
            // 此处的mLastRotation是先在上面引用再在下面定义的，这个本人不理解，
            // 有知道的朋友可以解释下这种方式，这种方式编译没错，但好像得不到想要的结果。
            int mLastRotation = -1;
        };
        
        if(orientationEventListener.canDetectOrientation())
            orientationEventListener.enable();

        // Apply screen orientation
        // 应用屏幕方向
        mActivity.setRequestedOrientation(screenOrientation);
        
        // As long as this window is visible to the user, keep the device's
        // screen turned on and bright:
        // 窗口是可见的话则保持光亮和可见
        mActivity.getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mVuforiaFlags = Vuforia.GL_20;  // 常量1
        
        // Initialize Vuforia SDK asynchronously to avoid blocking the
        // main (UI) thread.
        //
        // NOTE: This task instance must be created and invoked on the
        // UI thread and it can be executed only once!
        // 异步初始化Vuforia SDK，防止UI主线程堵塞
        // 注：此任务实例必须在用户界面线程上创建和调用，它可以只执行一次！
        if (mInitVuforiaTask != null)
        {
            String logMessage = "Cannot initialize SDK twice";
            vuforiaException = new SampleApplicationException(
                SampleApplicationException.VUFORIA_ALREADY_INITIALIZATED,
                logMessage);
            Log.e(LOGTAG, logMessage);
        }
        
        if (vuforiaException == null)
        {
            try
            {
                // 新建一个InitVuforiaTask to init
                mInitVuforiaTask = new InitVuforiaTask();
                mInitVuforiaTask.execute();
            } catch (Exception e)
            {
                String logMessage = "Initializing Vuforia SDK failed";
                vuforiaException = new SampleApplicationException(
                    SampleApplicationException.INITIALIZATION_FAILURE,
                    logMessage);
                Log.e(LOGTAG, logMessage);
            }
        }
        
        if (vuforiaException != null)
            mSessionControl.onInitARDone(vuforiaException);
    }
    
    /**
     * 启动AR，初始化并运行相机，运行追踪器
     * @param camera
     * @throws SampleApplicationException
     */
    public void startAR(int camera) throws SampleApplicationException
    {
        String error;
        if(mCameraRunning)
        {
        	error = "Camera already running, unable to open again";
        	Log.e(LOGTAG, error);
            throw new SampleApplicationException(
                SampleApplicationException.CAMERA_INITIALIZATION_FAILURE, error);
        }
        
        mCamera = camera;

        // 初始化相机设备
        if (!CameraDevice.getInstance().init(camera))
        {
            error = "Unable to open camera device: " + camera;
            Log.e(LOGTAG, error);
            throw new SampleApplicationException(
                SampleApplicationException.CAMERA_INITIALIZATION_FAILURE, error);
        }

        // 选择视频模式
        if (!CameraDevice.getInstance().selectVideoMode(
            CameraDevice.MODE.MODE_DEFAULT))
        {
            error = "Unable to set video mode";
            Log.e(LOGTAG, error);
            throw new SampleApplicationException(
                SampleApplicationException.CAMERA_INITIALIZATION_FAILURE, error);
        }

        // 启动相机
        if (!CameraDevice.getInstance().start())
        {
            error = "Unable to start camera device: " + camera;
            Log.e(LOGTAG, error);
            throw new SampleApplicationException(
                SampleApplicationException.CAMERA_INITIALIZATION_FAILURE, error);
        }

        // 启动追踪器
        mSessionControl.doStartTrackers();

        // 记录相机已经启动
        mCameraRunning = true;
        
        if(!CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO))
        {
            if(!CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_TRIGGERAUTO))
                CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_NORMAL);
        }
    }
    
    
    // Stops any ongoing initialization, stops Vuforia
    public void stopAR() throws SampleApplicationException
    {
        // Cancel potentially running tasks
        if (mInitVuforiaTask != null
            && mInitVuforiaTask.getStatus() != InitVuforiaTask.Status.FINISHED)
        {
            mInitVuforiaTask.cancel(true);
            mInitVuforiaTask = null;
        }
        
        if (mLoadTrackerTask != null
            && mLoadTrackerTask.getStatus() != LoadTrackerTask.Status.FINISHED)
        {
            mLoadTrackerTask.cancel(true);
            mLoadTrackerTask = null;
        }
        
        mInitVuforiaTask = null;
        mLoadTrackerTask = null;
        
        mStarted = false;
        
        stopCamera();
        
        // Ensure that all asynchronous operations to initialize Vuforia
        // and loading the tracker datasets do not overlap:
        synchronized (mShutdownLock)
        {
            
            boolean unloadTrackersResult;
            boolean deinitTrackersResult;
            
            // Destroy the tracking data set:
            unloadTrackersResult = mSessionControl.doUnloadTrackersData();
            
            // Deinitialize the trackers:
            deinitTrackersResult = mSessionControl.doDeinitTrackers();
            
            // Deinitialize Vuforia SDK:
            Vuforia.deinit();
            
            if (!unloadTrackersResult)
                throw new SampleApplicationException(
                    SampleApplicationException.UNLOADING_TRACKERS_FAILURE,
                    "Failed to unload trackers\' data");
            
            if (!deinitTrackersResult)
                throw new SampleApplicationException(
                    SampleApplicationException.TRACKERS_DEINITIALIZATION_FAILURE,
                    "Failed to deinitialize trackers");
            
        }
    }
    
    
    // Resumes Vuforia, restarts the trackers and the camera
    public void resumeAR() throws SampleApplicationException
    {
        // Vuforia-specific resume operation
        Vuforia.onResume();
        
        if (mStarted)
        {
            startAR(mCamera);
        }
    }
    
    
    // Pauses Vuforia and stops the camera
    public void pauseAR() throws SampleApplicationException
    {
        if (mStarted)
        {
            stopCamera();
        }
        
        Vuforia.onPause();
    }

    /**
     * 每一个周期进行回调
     * Q:什么周期
     * @param s
     */
    @Override
    public void Vuforia_onUpdate(State s)
    {
        mSessionControl.onVuforiaUpdate(s);
    }
    
    
    // Manages the configuration changes
    public void onConfigurationChanged()
    {
        Device.getInstance().setConfigurationChanged();
    }
    
    
    // Methods to be called to handle lifecycle
    public void onResume()
    {
        Vuforia.onResume();
    }
    
    
    public void onPause()
    {
        Vuforia.onPause();
    }
    
    
    public void onSurfaceChanged(int width, int height)
    {
        Vuforia.onSurfaceChanged(width, height);
    }
    
    
    public void onSurfaceCreated()
    {
        Vuforia.onSurfaceCreated();
    }
    
    /**
     * 初始化Vuforia的异步任务类
     */
    private class InitVuforiaTask extends AsyncTask<Void, Integer, Boolean>
    {
        // 初始化进度值为一个无效值
        private int mProgressValue = -1;

        protected Boolean doInBackground(Void... params)
        {

            // doInBackground(Params…) 后台执行，比较耗时的操作都可以放在这里。
            // 注意这里不能直接操作UI。
            // 此方法在后台线程执行，完成任务的主要工作，通常需要较长的时间。
            // 在执行过程中可以调用
            // http://www.cnblogs.com/devinzhang/archive/2012/02/13/2350070.html
            synchronized (mShutdownLock)
            {
                Vuforia.setInitParameters(mActivity, mVuforiaFlags,
                        "AaG3I6T/////AAAAGSQzUxGkMU8WpTGRpvwrPX1kgko7p+qHnlxtsDTUwktDjK0VqAlXnzoo9TA2ZXa2kXiikelYSdfmWt53YN4xw0R/1f1peAarMCQTXn/Kmfok2zZGRtWLXS8pXUA00ToOwaj9H9lI9YDl7K/KUpgL9dZj5UEyJwEl/wwyTSd3q25btwDqzEAKGw5INBQphbZPCTL8+GMyc2d9F+PfBfyw2AKu3HjizpV1ooLYxJlfkK2FoNrzKS5yPlh1utyuOv8IGtsoelnypS3X86uEYh3WcwwcDI4EkF3bvxqCmddEZ9llRwXEBclfJdeiLJYOrpHktcSdcQ++35SJGRSFRgK14HOGpgM+XN9rOTmDshk5dOJx");

                // 等待初始化完成
                do
                {
                    // Vuforia.init()进行初始化，返回初始化进度(0~100)，
                    // 当返回值为100时表示初始化完成，
                    // 当返回值为-1时表示初始化出错
                    mProgressValue = Vuforia.init();
                    
                    // 更新进度信息
                    publishProgress(mProgressValue);
                    
                    // 当没有被取消，并且完成百分比在0-100时继续进行初始化
                } while (!isCancelled() && mProgressValue >= 0
                    && mProgressValue < 100);

                return (mProgressValue > 0);
            }
        }
        
        // 在调用publishProgress()时执行
        protected void onProgressUpdate(Integer... values)
        {
            // 处理一些与进度值有关的事情，例如更新进度条显示等
        }

        // 使用在doInBackground得到的结果来处理操作UI。
        // 此方法在主线程执行，任务执行的结果作为此方法的参数返回
        protected void onPostExecute(Boolean result)
        {
            // Vuforia初始化完成，进行应用程序的下一步初始化工作
            SampleApplicationException vuforiaException = null;
            
            if (result)
            {
                Log.d(LOGTAG, "InitVuforiaTask.onPostExecute: Vuforia "
                    + "initialization successful");
                
                boolean initTrackersResult;
                // 初始化跟踪器
                initTrackersResult = mSessionControl.doInitTrackers();
                
                if (initTrackersResult)
                {
                    try
                    {
                        // 创建异步加载跟踪器数据类
                        mLoadTrackerTask = new LoadTrackerTask();
                        mLoadTrackerTask.execute();
                    } catch (Exception e)
                    {
                        String logMessage = "Loading tracking data set failed";
                        vuforiaException = new SampleApplicationException(
                            SampleApplicationException.LOADING_TRACKERS_FAILURE,
                            logMessage);
                        Log.e(LOGTAG, logMessage);
                        mSessionControl.onInitARDone(vuforiaException);
                    }
                    
                } else
                {
                    vuforiaException = new SampleApplicationException(
                        SampleApplicationException.TRACKERS_INITIALIZATION_FAILURE,
                        "Failed to initialize trackers");
                    mSessionControl.onInitARDone(vuforiaException);
                }
            } else
            {
                String logMessage;

                // 如果初始化失败则表明设备不支持，应通知用户消息
                // 获取错误信息
                logMessage = getInitializationErrorString(mProgressValue);

                Log.e(LOGTAG, "InitVuforiaTask.onPostExecute: " + logMessage
                    + " Exiting.");
                
                // 发送异常并停止初始化
                vuforiaException = new SampleApplicationException(
                    SampleApplicationException.INITIALIZATION_FAILURE,
                    logMessage);
                mSessionControl.onInitARDone(vuforiaException);
            }
        }
    }

    /**
     * 异步加载跟踪器数据类
     */
    private class LoadTrackerTask extends AsyncTask<Void, Integer, Boolean>
    {
        protected Boolean doInBackground(Void... params)
        {
            // 防止onDestroy()方法叠加
            synchronized (mShutdownLock)
            {
                // 加载跟踪器数据集
                return mSessionControl.doLoadTrackersData();
            }
        }

        protected void onPostExecute(Boolean result)
        {
            SampleApplicationException vuforiaException = null;
            
            Log.d(LOGTAG, "LoadTrackerTask.onPostExecute: execution "
                + (result ? "successful" : "failed"));
            
            if (!result)
            {
                String logMessage = "Failed to load tracker data.";
                // 数据集加载出错
                Log.e(LOGTAG, logMessage);
                vuforiaException = new SampleApplicationException(
                    SampleApplicationException.LOADING_TRACKERS_FAILURE,
                    logMessage);
            } else
            {
                // 提示系统进行垃圾回收，但是无法保证系统真的会进行垃圾回收
                System.gc();
                // 注册回调，当SDK正确加载并跟踪结束之后回调
                // 回调响应函数为 Vuforia_onUpdate()
                Vuforia.registerCallback(SampleApplicationSession.this);
                
                mStarted = true;
            }
            
            // 完成加载跟踪，更新应用程序状态，发送异常检查错误
            mSessionControl.onInitARDone(vuforiaException);
        }
    }
    
    // 根据错误代码返回错误信息
    private String getInitializationErrorString(int code)
    {
        if (code == Vuforia.INIT_DEVICE_NOT_SUPPORTED)
            return mActivity.getString(R.string.INIT_ERROR_DEVICE_NOT_SUPPORTED);
        if (code == Vuforia.INIT_NO_CAMERA_ACCESS)
            return mActivity.getString(R.string.INIT_ERROR_NO_CAMERA_ACCESS);
        if (code == Vuforia.INIT_LICENSE_ERROR_MISSING_KEY)
            return mActivity.getString(R.string.INIT_LICENSE_ERROR_MISSING_KEY);
        if (code == Vuforia.INIT_LICENSE_ERROR_INVALID_KEY)
            return mActivity.getString(R.string.INIT_LICENSE_ERROR_INVALID_KEY);
        if (code == Vuforia.INIT_LICENSE_ERROR_NO_NETWORK_TRANSIENT)
            return mActivity.getString(R.string.INIT_LICENSE_ERROR_NO_NETWORK_TRANSIENT);
        if (code == Vuforia.INIT_LICENSE_ERROR_NO_NETWORK_PERMANENT)
            return mActivity.getString(R.string.INIT_LICENSE_ERROR_NO_NETWORK_PERMANENT);
        if (code == Vuforia.INIT_LICENSE_ERROR_CANCELED_KEY)
            return mActivity.getString(R.string.INIT_LICENSE_ERROR_CANCELED_KEY);
        if (code == Vuforia.INIT_LICENSE_ERROR_PRODUCT_TYPE_MISMATCH)
            return mActivity.getString(R.string.INIT_LICENSE_ERROR_PRODUCT_TYPE_MISMATCH);
        else
        {
            return mActivity.getString(R.string.INIT_LICENSE_ERROR_UNKNOWN_ERROR);
        }
    }
    
    // 停止摄像头
    public void stopCamera()
    {
        if (mCameraRunning)
        {
            mSessionControl.doStopTrackers();
            mCameraRunning = false;
            CameraDevice.getInstance().stop();
            CameraDevice.getInstance().deinit();
        }
    }
    
    
    // Returns true if Vuforia is initialized, the trackers started and the
    // tracker data loaded
    // Q:在哪调用？
    private boolean isARRunning()
    {
        return mStarted;
    }
    
}
