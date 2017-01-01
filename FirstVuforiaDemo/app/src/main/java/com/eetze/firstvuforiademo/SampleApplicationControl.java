package com.eetze.firstvuforiademo;

import com.vuforia.State;


// Interface to be implemented by the activity which uses SampleApplicationSession
// 使用SampleApplicationSession所需实现接口
public interface SampleApplicationControl
{
    
    // To be called to initialize the trackers
    // 初始化跟踪器
    boolean doInitTrackers();
    
    
    // To be called to load the trackers' data
    // 加载跟踪器数据
    boolean doLoadTrackersData();
    
    
    // To be called to start tracking with the initialized trackers and their
    // loaded data
    // 开始跟踪
    boolean doStartTrackers();
    
    
    // To be called to stop the trackers
    // 停止跟踪
    boolean doStopTrackers();
    
    
    // To be called to destroy the trackers' data
    // 销毁跟踪器数据
    boolean doUnloadTrackersData();
    
    
    // To be called to deinitialize the trackers
    // 取消跟踪器的初始化
    boolean doDeinitTrackers();
    
    
    // This callback is called after the Vuforia initialization is complete,
    // the trackers are initialized, their data loaded and
    // tracking is ready to start
    // 回调函数
    // 跟踪器初始化、跟踪器数据加载、并且启动后调用
    void onInitARDone(SampleApplicationException e);
    
    
    // This callback is called every cycle
    // 回调函数
    // 每个周期结束后调用
    void onVuforiaUpdate(State state);
    
}
