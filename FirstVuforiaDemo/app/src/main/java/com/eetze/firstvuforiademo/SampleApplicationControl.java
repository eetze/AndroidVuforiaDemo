package com.eetze.firstvuforiademo;

import com.vuforia.State;

/**
 * 应用控制器接口类
 */
public interface SampleApplicationControl
{
    /**
     * 初始化跟踪器
     * @return
     */
    boolean doInitTrackers();

    /**
     * 加载跟踪器数据
     * @return
     */
    boolean doLoadTrackersData();

    /**
     * 开始跟踪
     * @return
     */
    boolean doStartTrackers();

    /**
     * 停止跟踪
     * @return
     */
    boolean doStopTrackers();
    
    /**
     * 销毁跟踪器数据
     * @return
     */
    boolean doUnloadTrackersData();

    /**
     * 取消跟踪器的初始化
     * @return
     */
    boolean doDeinitTrackers();
    
    /**
     * 回调函数，跟踪器初始化、跟踪器数据加载、并且启动后调用
     * @param e
     */
    void onInitARDone(SampleApplicationException e);
    
    /**
     * 回调函数，每个周期结束后调用
     * @param state
     */
    void onVuforiaUpdate(State state);
    
}
