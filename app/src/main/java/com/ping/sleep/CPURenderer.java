package com.ping.sleep;

import android.content.Context;
import android.graphics.PointF;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// CPU渲染器，使用Canvas进行渲染
public class CPURenderer {
    private static final String TAG = "CPURenderer";
    
    // 渲染状态枚举
    public enum State { CONVERGE, WRITING, FLASH }
    
    // 粒子结构
    public static class Particle {
        public float x, y;      // 位置
        public float vx, vy;    // 速度
        public float life;      // 生命周期
        public float r, g, b, a; // 颜色
        
        public Particle(float x, float y) {
            this.x = x;
            this.y = y;
            this.vx = 0;
            this.vy = 0;
            this.life = 1.0f;
            this.r = 0.9f;
            this.g = 0.72f;
            this.b = 0.0f;
            this.a = 1.0f;
        }
    }
    
    // 闪烁粒子结构
    public static class FlashParticle {
        public float x, y;
        public float vx, vy;
        public float phase;
        public float speed;
    }
    
    private Context context;
    private String quoteText = "子瑜，晚安";
    private AnimationListener listener;
    private boolean isRunning = true;
    private boolean isFlashing = true;
    
    private static final int PARTICLE_COUNT = 400; // 进一步减少粒子数量以提升性能
    private static final float CONVERGE_DURATION = 3.5f; // 缩短动画时长，提高流畅度
    private static final float PER_CHAR_TIME = 1.8f;
    private static final float MAX_WRITING_TIME = 7.0f;
    private static final float GRAVITY_STRENGTH = 1200.0f; // 增加重力强度，加快收敛
    private static final float ABSORB_RADIUS = 15.0f; // 增加吸收半径
    
    private State currentState = State.CONVERGE;
    private float convergeTimer = 0;
    private float writingTimer = 0;
    private float flashTimer = 0;
    private float totalWritingTime;
    private long lastTimeNs;
    
    private List<PointF> textPoints = new ArrayList<>();
    private int totalPoints;
    private float blackHoleX, blackHoleY;
    private List<PointF> writtenPath = new ArrayList<>();
    
    private List<Particle> particles = new ArrayList<>();
    private List<FlashParticle> flashParticles = new ArrayList<>();
    private List<FlashParticle> flashParticlePool = new ArrayList<>(); // 对象池
    
    private int screenWidth, screenHeight;
    private Random random = new Random();
    
    public CPURenderer(Context context) {
        this.context = context;
        initParticles();
    }
    
    public void setQuote(String quote) {
        this.quoteText = quote;
    }
    
    public void setListener(AnimationListener listener) {
        this.listener = listener;
    }
    
    public void start() {
        isRunning = true;
        currentState = State.CONVERGE;
        convergeTimer = 0;
        writingTimer = 0;
        flashTimer = 0;
        isFlashing = true;
        lastTimeNs = System.nanoTime();
        writtenPath.clear();
        initTextPath();
    }
    
    public void stop() {
        isRunning = false;
    }
    
    public void toggleFlash() {
        isFlashing = !isFlashing;
    }
    
    public void destroy() {
        particles.clear();
        flashParticles.clear();
        flashParticlePool.clear();
        textPoints.clear();
        writtenPath.clear();
    }
    
    private void initParticles() {
        particles.clear();
        if (screenWidth == 0 || screenHeight == 0) return;
        
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double radius = Math.max(screenWidth, screenHeight) * 0.8 + random.nextDouble() * 100;
            float x = (float) (screenWidth / 2 + radius * Math.cos(angle));
            float y = (float) (screenHeight / 2 + radius * Math.sin(angle));
            particles.add(new Particle(x, y));
        }
    }
    
    private void initTextPath() {
        textPoints.clear();
        
        // 简化的文本路径生成
        if (quoteText == null || quoteText.isEmpty()) {
            // 使用默认路径
            for (int i = 0; i <= 100; i++) {
                float t = i / 100f;
                float x = screenWidth * 0.2f + t * screenWidth * 0.6f;
                float y = screenHeight * 0.3f + t * screenHeight * 0.4f;
                textPoints.add(new PointF(x, y));
            }
        } else {
            // 为每个字符创建简单路径点
            int charCount = quoteText.length();
            for (int i = 0; i < charCount; i++) {
                float progress = (float) i / charCount;
                float x = screenWidth * 0.3f + progress * screenWidth * 0.4f;
                float y = screenHeight * 0.5f;
                textPoints.add(new PointF(x, y));
            }
        }
        
        totalPoints = textPoints.size();
        totalWritingTime = Math.min(quoteText.length() * PER_CHAR_TIME, MAX_WRITING_TIME);
    }
    
    public void update(long width, long height) {
        if (!isRunning) return;
        
        // 更新屏幕尺寸
        if (width != screenWidth || height != screenHeight) {
            screenWidth = (int) width;
            screenHeight = (int) height;
            initParticles();
            initTextPath();
        }
        
        long now = System.nanoTime();
        float deltaTime = (now - lastTimeNs) / 1e9f;
        if (deltaTime > 0.1f) deltaTime = 0.016f;
        lastTimeNs = now;
        
        switch (currentState) {
            case CONVERGE:
                convergeTimer += deltaTime;
                updateConverge(deltaTime);
                if (convergeTimer >= CONVERGE_DURATION) {
                    currentState = State.WRITING;
                    writingTimer = 0;
                    if (!textPoints.isEmpty()) {
                        blackHoleX = textPoints.get(0).x;
                        blackHoleY = textPoints.get(0).y;
                    }
                    writtenPath.clear();
                }
                break;
            case WRITING:
                writingTimer += deltaTime;
                if (totalPoints > 0) {
                    float progress = Math.min(1.0f, writingTimer / totalWritingTime);
                    int targetIndex = (int) (progress * totalPoints);
                    if (targetIndex >= totalPoints) targetIndex = totalPoints - 1;
                    blackHoleX = textPoints.get(targetIndex).x;
                    blackHoleY = textPoints.get(targetIndex).y;
                    for (int i = writtenPath.size(); i <= targetIndex; i++) {
                        writtenPath.add(textPoints.get(i));
                    }
                }
                if (writingTimer >= totalWritingTime) {
                    currentState = State.FLASH;
                    initFlashParticles();
                }
                break;
            case FLASH:
                flashTimer += deltaTime;
                if (isFlashing) {
                    updateFlashParticles(deltaTime);
                }
                // 检查动画是否完成
                if (flashTimer > 2.0f && listener != null) {
                    listener.onAnimationFinished();
                }
                break;
        }
    }
    
    private void updateConverge(float dt) {
        float cx = screenWidth / 2f;
        float cy = screenHeight / 2f;
        float k = GRAVITY_STRENGTH;
        
        for (Particle p : particles) {
            float dx = cx - p.x;
            float dy = cy - p.y;
            float r2 = dx*dx + dy*dy + 1e-5f;
            float r = (float) Math.sqrt(r2);
            float f = k / r2;
            float ax = f * dx / r;
            float ay = f * dy / r;
            
            p.vx += ax * dt;
            p.vy += ay * dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            
            if (r < ABSORB_RADIUS) {
                p.x = cx;
                p.y = cy;
                p.life = Math.max(0, p.life - dt * 2);
            }
        }
    }
    
    private void initFlashParticles() {
        flashParticles.clear();
        
        int charCount = quoteText.length();
        if (charCount <= 0) return;
        
        for (int c = 0; c < charCount; c++) {
            int start = (c * totalPoints) / charCount;
            int end = ((c + 1) * totalPoints) / charCount;
            float cx = 0, cy = 0;
            for (int i = start; i < end; i++) {
                cx += textPoints.get(i).x;
                cy += textPoints.get(i).y;
            }
            int count = end - start;
            if (count > 0) {
                cx /= count;
                cy /= count;
            } else {
                cx = screenWidth / 2f;
                cy = screenHeight / 2f;
            }
            
            for (int j = 0; j < 8; j++) { // FLASH_PARTICLE_COUNT_PER_CHAR
                FlashParticle p;
                if (flashParticlePool.isEmpty()) {
                    p = new FlashParticle();
                } else {
                    p = flashParticlePool.remove(flashParticlePool.size() - 1);
                }
                
                double angle = random.nextDouble() * 2 * Math.PI;
                float dist = 30 + random.nextFloat() * 50;
                p.x = cx + (float) (dist * Math.cos(angle));
                p.y = cy + (float) (dist * Math.sin(angle));
                p.vx = (random.nextFloat() - 0.5f) * 20;
                p.vy = (random.nextFloat() - 0.5f) * 20;
                p.phase = random.nextFloat() * (float) (2 * Math.PI);
                p.speed = 2 + random.nextFloat() * 3;
                flashParticles.add(p);
            }
        }
    }
    
    private void updateFlashParticles(float dt) {
        for (FlashParticle p : flashParticles) {
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            if (p.x < 0 || p.x > screenWidth) p.vx = -p.vx;
            if (p.y < 0 || p.y > screenHeight) p.vy = -p.vy;
        }
    }
    
    public List<Particle> getParticles() {
        return particles;
    }
    
    public List<FlashParticle> getFlashParticles() {
        return flashParticles;
    }
    
    public State getCurrentState() {
        return currentState;
    }
    
    public boolean isFlashing() {
        return isFlashing;
    }
    
    public float getAlpha() {
        return (currentState == State.FLASH && !isFlashing) ? 0.3f : 1.0f;
    }
}