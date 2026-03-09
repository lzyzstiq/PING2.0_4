package com.ping.sleep;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.Log;

import androidx.core.content.res.ResourcesCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// CPU渲染器，使用Canvas进行渲染
public class ParticleCPURenderer {
    private static final String TAG = "ParticleCPURenderer";

    private enum State { CONVERGE, WRITING, FLASH }

    private static final int PARTICLE_COUNT = 500; // 减少粒子数量以提升性能
    private static final float CONVERGE_DURATION = 4.0f;
    private static final float PER_CHAR_TIME = 2.0f;
    private static final float MAX_WRITING_TIME = 8.0f;
    private static final float GRAVITY_STRENGTH = 1000.0f;
    private static final float ABSORB_RADIUS = 10.0f;
    private static final float PARTICLE_BASE_SIZE = 4.0f; // 减小粒子大小
    private static final float[] PARTICLE_COLOR = {0.9f, 0.72f, 0.0f, 1.0f};
    private static final float FLASH_PARTICLE_COUNT_PER_CHAR = 8; // 减少闪烁粒子数量

    private Context context;
    private String quoteText = "子瑜，晚安";  // 默认语录，稍后会被 setQuote 替换
    private AnimationListener listener;
    private boolean isRunning = true;
    private boolean isFlashing = true;

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

    private static class FlashParticle {
        float x, y;
        float vx, vy;
        float phase;
        float speed;
    }
    private List<Particle> particles = new ArrayList<>();
    private List<FlashParticle> flashParticles = new ArrayList<>();
    private List<FlashParticle> flashParticlePool = new ArrayList<>(); // 对象池

    private int screenWidth, screenHeight;
    private Random random = new Random();
    private Paint particlePaint;
    private Paint flashParticlePaint;

    // 构造函数：只保存 Context，不做任何需要 SharedPreferences 的操作
    public ParticleCPURenderer(Context context) {
        this.context = context;
        initPaints();
    }

    private void initPaints() {
        particlePaint = new Paint();
        particlePaint.setAntiAlias(true);
        particlePaint.setStyle(Paint.Style.FILL);

        flashParticlePaint = new Paint();
        flashParticlePaint.setAntiAlias(true);
        flashParticlePaint.setStyle(Paint.Style.FILL);
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
        initParticles();
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
        if (flashParticles != null) {
            // 将使用过的粒子返回到对象池而不是丢弃
            flashParticlePool.addAll(flashParticles);
            flashParticles.clear();
            flashParticles = null;
        }
        if (flashParticlePool != null) {
            flashParticlePool.clear();
            flashParticlePool = null;
        }
        if (textPoints != null) {
            textPoints.clear();
            textPoints = null;
        }
        if (writtenPath != null) {
            writtenPath.clear();
            writtenPath = null;
        }
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
        Typeface typeface;
        try {
            typeface = ResourcesCompat.getFont(context, R.font.my_font);
        } catch (Exception e) {
            typeface = Typeface.DEFAULT;
        }
        if (typeface == null) typeface = Typeface.DEFAULT;

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(typeface);
        paint.setTextSize(200);

        float maxWidth = screenWidth * 0.8f;
        List<String> lines = wrapText(quoteText, paint, maxWidth);
        float lineSpacing = 1.2f;
        float yOffset = 0;

        textPoints.clear();

        if (lines.isEmpty() || quoteText.isEmpty()) {
            Log.w(TAG, "initTextPath: using fallback diagonal line");
            for (int i = 0; i <= 100; i++) {
                float t = i / 100f;
                float x = screenWidth * 0.2f + t * screenWidth * 0.6f;
                float y = screenHeight * 0.3f + t * screenHeight * 0.4f;
                textPoints.add(new PointF(x, y));
            }
        } else {
            for (String line : lines) {
                Path path = new Path();
                paint.getTextPath(line, 0, line.length(), 0, yOffset, path);
                PathMeasure pm = new PathMeasure(path, false);
                float length = pm.getLength();
                int steps = (int) (length / 2);
                for (int i = 0; i <= steps; i++) {
                    float[] pos = new float[2];
                    pm.getPosTan(i * 2, pos, null);
                    textPoints.add(new PointF(pos[0], pos[1]));
                }
                yOffset += paint.getTextSize() * lineSpacing;
            }

            Rect bounds = new Rect();
            paint.getTextBounds(quoteText, 0, quoteText.length(), bounds);
            float totalHeight = lines.size() * paint.getTextSize() * lineSpacing;
            float offsetX = (screenWidth - bounds.width()) / 2f - bounds.left;
            float offsetY = (screenHeight - totalHeight) / 2f;
            for (PointF p : textPoints) {
                p.x += offsetX;
                p.y += offsetY;
            }
        }

        totalPoints = textPoints.size();
        totalWritingTime = Math.min(quoteText.length() * PER_CHAR_TIME, MAX_WRITING_TIME);
        Log.d(TAG, "initTextPath: totalPoints=" + totalPoints);
    }

    private List<String> wrapText(String text, Paint paint, float maxWidth) {
        List<String> lines = new ArrayList<>();
        if (maxWidth <= 0) {
            lines.add(text);
            return lines;
        }
        String remaining = text;
        while (!remaining.isEmpty()) {
            int count = paint.breakText(remaining, true, maxWidth, null);
            if (count == 0) break;
            lines.add(remaining.substring(0, count));
            remaining = remaining.substring(count);
        }
        return lines;
    }

    public void update(int width, int height) {
        if (!isRunning) return;

        // 更新屏幕尺寸
        if (width != screenWidth || height != screenHeight) {
            screenWidth = width;
            screenHeight = height;
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
        
        // 预先计算需要的粒子总数并复用对象池中的对象
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

            for (int j = 0; j < FLASH_PARTICLE_COUNT_PER_CHAR; j++) {
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
        int size = flashParticles.size();
        for (int i = 0; i < size; i++) {
            FlashParticle p = flashParticles.get(i);
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            if (p.x < 0 || p.x > screenWidth) p.vx = -p.vx;
            if (p.y < 0 || p.y > screenHeight) p.vy = -p.vy;
        }
    }

    public void draw(Canvas canvas) {
        if (canvas == null) return;
        
        float alpha = (currentState == State.FLASH && !isFlashing) ? 0.3f : 1.0f;
        
        // 绘制普通粒子
        for (Particle p : particles) {
            particlePaint.setColor(Color.argb(
                (int)(alpha * 255),
                (int)(p.r * 255),
                (int)(p.g * 255),
                (int)(p.b * 255)
            ));
            canvas.drawCircle(p.x, p.y, PARTICLE_BASE_SIZE, particlePaint);
        }
        
        // 绘制闪烁粒子
        if (isFlashing) {
            for (FlashParticle p : flashParticles) {
                flashParticlePaint.setColor(Color.argb(
                    (int)(alpha * 255),
                    255,
                    255,
                    200
                ));
                canvas.drawCircle(p.x, p.y, PARTICLE_BASE_SIZE * 1.5f, flashParticlePaint);
            }
        }
    }

    public State getCurrentState() {
        return currentState;
    }

    public boolean isFlashing() {
        return isFlashing;
    }

    public List<Particle> getParticles() {
        return particles;
    }
}
