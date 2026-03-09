package com.ping.sleep;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public class SleepReminderWorker extends Worker {
    private static final String TAG = "SleepReminderWorker";

    public SleepReminderWorker(Context context, WorkerParameters params) {
        super(context, params);
    }

    @Override
    public Result doWork() {
        Log.d(TAG, "SleepReminderWorker executed");
        
        // 这里可以执行与SleepAlarmReceiver类似的通知逻辑
        // 但由于Worker无法直接访问UI，我们需要使用通知
        Context context = getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences("ping_prefs", Context.MODE_PRIVATE);
        
        // 获取随机语录
        String quote = QuoteManager.getRandomQuote(context, prefs);
        
        // 创建通知
        NotificationHelper.showNotification(context, quote);
        
        return Result.success();
    }

    // 设置睡眠提醒工作
    public static void setSleepReminder(Context context, SharedPreferences prefs) {
        int hour = prefs.getInt("start_hour", 23);
        int minute = prefs.getInt("start_minute", 0);
        int intervalMinutes = prefs.getInt("interval_minutes", 30);

        // 计算从当前时间到第一次执行的时间间隔
        Calendar now = Calendar.getInstance();
        Calendar firstRun = Calendar.getInstance();
        firstRun.set(Calendar.HOUR_OF_DAY, hour);
        firstRun.set(Calendar.MINUTE, minute);
        firstRun.set(Calendar.SECOND, 0);
        
        // 如果已过今天的提醒时间，则设置为明天
        if (firstRun.getTimeInMillis() <= now.getTimeInMillis()) {
            firstRun.add(Calendar.DAY_OF_YEAR, 1);
        }

        long initialDelay = firstRun.getTimeInMillis() - now.getTimeInMillis();
        long repeatInterval = TimeUnit.MINUTES.toMillis(intervalMinutes);
        
        // 确保重复间隔至少为15分钟（WorkManager的最小间隔）
        repeatInterval = Math.max(repeatInterval, TimeUnit.MINUTES.toMillis(15));

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(false)
                .setRequiresCharging(false)
                .build();

        PeriodicWorkRequest sleepReminderWork = new PeriodicWorkRequest.Builder(
                SleepReminderWorker.class,
                repeatInterval,
                TimeUnit.MILLISECONDS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                        "SleepReminder",
                        ExistingPeriodicWorkPolicy.UPDATE,
                        sleepReminderWork);
        
        Log.d(TAG, "Sleep reminder scheduled with initial delay: " + initialDelay + "ms, repeat: " + repeatInterval + "ms");
    }

    // 取消睡眠提醒工作
    public static void cancelSleepReminder(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork("SleepReminder");
        Log.d(TAG, "Sleep reminder cancelled");
    }
}