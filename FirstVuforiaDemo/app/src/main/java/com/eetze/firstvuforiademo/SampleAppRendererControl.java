package com.eetze.firstvuforiademo;

import com.vuforia.State;

/**
 * 渲染器控制器接口类
 */
public interface SampleAppRendererControl
{

    /**
     * 需要被每一个渲染器实例实现，SampleAppRendering每个视图的循环中调用
     * @param state
     * @param projectionMatrix
     */
    void renderFrame(State state, float[] projectionMatrix);
}
