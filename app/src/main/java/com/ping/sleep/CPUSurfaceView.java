package com.ping.sleep;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

// 使用SurfaceView实现的CPU渲染视图
public class CPUSurfaceView extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    private static final String TAG = "CPUSurfaceView";
    
    private volatile boolean isRunning = false;
    private Thread renderThread;
    private SurfaceHolder surfaceHolder;
    
    private CPURenderer cpuRenderer;
    private Paint particlePaint;
    private Paint backgroundPaint;
    
    public VulkanSurfaceView(Context context) {
        super(context);
        init();
    }
    
    public VulkanSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public VulkanSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        surfaceHolder = getHolder();
        surfaceHolder.addCallback(this);
        
        // 初始化渲染器
        cpuRenderer = new CPURenderer(getContext());
        
        // 初始化画笔
        particlePaint = new Paint();
        particlePaint.setAntiAlias(true);
        particlePaint.setStyle(Paint.Style.FILL);
        
        backgroundPaint = new Paint();
        backgroundPaint.setColor(Color.BLACK);
    }
    
    public void setAnimationListener(AnimationListener listener) {
        cpuRenderer.setListener(listener);
    }
    
    public void startAnimation(String quote) {
        cpuRenderer.setQuote(quote);
        cpuRenderer.start();
        startRendering();
    }
    
    public void stopAnimation() {
        stopRendering();
    }
    
    private void startRendering() {
        isRunning = true;
        if (renderThread == null) {
            renderThread = new Thread(this);
            renderThread.start();
        }
    }
    
    private void stopRendering() {
        isRunning = false;
        if (renderThread != null) {
            try {
                renderThread.join();
                renderThread = null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    @Override
    public void run() {
        long frameTime = 1000 / 60; // 目标帧时间 (60 FPS)
        long lastFrameTime = System.currentTimeMillis();
        
        while (isRunning) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastFrameTime >= frameTime) {
                lastFrameTime = currentTime;
                
                if (surfaceHolder.getSurface().isValid()) {
                    Canvas canvas = null;
                    try {
                        canvas = surfaceHolder.lockCanvas();
                        if (canvas != null) {
                            // 清除画布
                            canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), backgroundPaint);
                            
                            // 更新渲染器
                            cpuRenderer.update(canvas.getWidth(), canvas.getHeight());
                            
                            // 绘制粒子
                            drawParticles(canvas);
                        }
                    } catch (Exception e) {
                        // 处理异常
                    } finally {
                        if (canvas != null) {
                            try {
                                surfaceHolder.unlockCanvasAndPost(canvas);
                            } catch (Exception e) {
                                // 忽略解锁异常
                            }
                        }
                    }
                }
            } else {
                // 短暂休眠以节省CPU资源
                long sleepTime = frameTime - (currentTime - lastFrameTime);
                if (sleepTime > 0) {
                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }
    
    private void drawParticles(Canvas canvas) {
        float alpha = cpuRenderer.getAlpha();
        
        // 绘制普通粒子
        for (CPURenderer.Particle particle : cpuRenderer.getParticles()) {
            particlePaint.setColor(Color.argb(
                (int)(alpha * 255),
                (int)(particle.r * 255),
                (int)(particle.g * 255),
                (int)(particle.b * 255)
            ));
            canvas.drawCircle(particle.x, particle.y, 4.0f, particlePaint);
        }
        
        // 绘制闪烁粒子
        if (cpuRenderer.isFlashing()) {
            for (CPURenderer.FlashParticle particle : cpuRenderer.getFlashParticles()) {
                particlePaint.setColor(Color.argb(
                    (int)(alpha * 255),
                    255,
                    255,
                    200
                ));
                canvas.drawCircle(particle.x, particle.y, 6.0f, particlePaint);
            }
        }
    }
    
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // Surface已创建，开始渲染
        if (isRunning) {
            startRendering();
        }
    }
    
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // Surface已改变，调整渲染器
    }
    
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // Surface被销毁，停止渲染
        stopRendering();
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopRendering();
        if (cpuRenderer != null) {
            cpuRenderer.destroy();
        }
    }
    
    public void setQuote(String quote) {
        cpuRenderer.setQuote(quote);
    }
    
    public void toggleFlash() {
        cpuRenderer.toggleFlash();
    }
}