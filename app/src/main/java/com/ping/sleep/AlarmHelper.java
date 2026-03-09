package com.ping.sleep;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import java.util.Calendar;

public class AlarmHelper {

    public static void setAlarm(Context context, SharedPreferences prefs) {
        int hour = prefs.getInt("start_hour", 23);
        int minute = prefs.getInt("start_minute", 0);
        int interval = prefs.getInt("interval_minutes", 30);

        // 首先尝试使用WorkManager作为主要方案
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 在Android 8.0及以上使用WorkManager
            SleepReminderWorker.setSleepReminder(context, prefs);
        } else {
            // 在较低版本使用AlarmManager
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(context, SleepAlarmReceiver.class);
            // 使用唯一请求码避免冲突
            int requestCode = generateRequestCode(hour, minute);
            
            // 使用适当的PendingIntent标志
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context, requestCode, intent, flags
            );

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, hour);
            cal.set(Calendar.MINUTE, minute);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
                cal.add(Calendar.DAY_OF_YEAR, 1);
            }
            long firstTime = cal.getTimeInMillis();

            // OriginOS和其他一些厂商系统限制精确闹钟权限
            // 因此我们只使用标准的闹钟设置，不检查canScheduleExactAlarms
            // 这样应用可以正常运行，但闹钟可能不够精确
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // 即使在支持精确闹钟的Android版本上，我们也使用标准闹钟以兼容OriginOS
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, firstTime, pendingIntent);
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, firstTime, pendingIntent);
            }

            prefs.edit().putLong("next_alarm_time", firstTime).apply();
        }
    }

    public static void cancelAlarm(Context context) {
        // 尝试取消WorkManager任务和AlarmManager闹钟
        SleepReminderWorker.cancelSleepReminder(context);
        
        SharedPreferences prefs = context.getSharedPreferences("ping_prefs", Context.MODE_PRIVATE);
        int hour = prefs.getInt("start_hour", 23);
        int minute = prefs.getInt("start_minute", 0);
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // 在较低版本使用AlarmManager取消
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(context, SleepAlarmReceiver.class);
            int requestCode = generateRequestCode(hour, minute);
            
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context, requestCode, intent, flags
            );
            alarmManager.cancel(pendingIntent);
            try {
                pendingIntent.cancel();
            } catch (Exception e) {
                Log.e("AlarmHelper", "Error canceling pending intent", e);
            }
        }
    }
    
    // 生成唯一的请求码
    private static int generateRequestCode(int hour, int minute) {
        return hour * 100 + minute; // 确保每个小时分钟组合都有唯一的请求码
    }
}
