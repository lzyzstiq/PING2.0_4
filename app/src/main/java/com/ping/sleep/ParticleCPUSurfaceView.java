package com.ping.sleep;

import android.content.Context;
import android.util.AttributeSet;

public class ParticleCPUSurfaceView extends CPUSurfaceView {
    public ParticleCPUSurfaceView(Context context) {
        super(context);
    }

    public ParticleCPUSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    // 保留相同的API以确保兼容性
    public void setAnimationListener(AnimationListener listener) {
        super.setAnimationListener(listener);
    }

    public void startAnimation(String quote) {
        super.startAnimation(quote);
    }

    public void stopAnimation() {
        super.stopAnimation();
    }

    public void toggleFlash() {
        super.toggleFlash();
    }
}
