package com.fullthrottle.app;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;

public class DrainService extends Service {
    static final String STOP = "com.fullthrottle.app.STOP";
    static final String CHANNEL = "discharge";
    static boolean active;
    static int level = -1;
    static float temperature;
    static boolean plugged;
    static String message = "准备就绪", gpu = "GPU 待机";
    static long remaining = -1;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final DischargeEstimator estimator = new DischargeEstimator();
    private LoadEngine engine;
    private PowerManager.WakeLock wakeLock;
    private boolean registered;
    static int target(Context c) { return c.getSharedPreferences("settings",MODE_PRIVATE).getInt("target",20); }
    static void readBattery(Intent i) {
        if(i==null) return;
        int raw=i.getIntExtra(BatteryManager.EXTRA_LEVEL,-1), scale=i.getIntExtra(BatteryManager.EXTRA_SCALE,100);
        level=raw>=0 && scale>0 ? raw*100/scale : -1;
        temperature=i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,0)/10f;
        plugged=i.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0;
    }
    private final BroadcastReceiver battery = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { readBattery(i); if(active) check(); }
    };
    @Override public void onCreate() {
        super.onCreate();
        NotificationChannel channel=new NotificationChannel(CHANNEL,"耗电运行状态",NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("显示剩余电量，并可立即停止耗电");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        readBattery(registerReceiver(battery,new IntentFilter(Intent.ACTION_BATTERY_CHANGED))); registered=true;
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent==null || STOP.equals(intent.getAction())) { finish("已停止"); return START_NOT_STICKY; }
        if(active) { check(); return START_NOT_STICKY; }
        if(Build.VERSION.SDK_INT>=34) startForeground(1,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(1,notification());
        if(level<0) { finish("无法读取电量"); return START_NOT_STICKY; }
        if(level<=target(this)) { finish("已达到停止电量"); return START_NOT_STICKY; }
        if(temperature>=50) { finish("电池温度过高，已停止"); return START_NOT_STICKY; }
        active=true; message="正在全速耗电"; gpu="GPU 启动中"; estimator.reset(); remaining=-1;
        try {
            wakeLock=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"FullThrottle:Discharge");
            wakeLock.acquire();
            engine=new LoadEngine();
            engine.start(value -> handler.post(() -> { if(active) gpu=value; }));
            handler.post(tick);
        } catch(RuntimeException e) { finish("无法启动负载，请重试"); }
        return START_NOT_STICKY;
    }
    private final Runnable tick=new Runnable() { public void run() {
        if(!active) return;
        readBattery(registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED)));
        check(); if(!active) return;
        long charge=getSystemService(BatteryManager.class).getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
        remaining=estimator.update(SystemClock.elapsedRealtime(),level,charge,plugged,target(DrainService.this));
        getSystemService(NotificationManager.class).notify(1,notification());
        handler.postDelayed(this,2000);
    }};
    private void check() {
        if(level<0) finish("无法读取电量，已停止");
        else if(level<=target(this)) finish("已达到 " + target(this) + "% · 自动停止");
        else if(temperature>=50) finish("电池达到 50°C · 已停止");
    }
    private Notification notification() {
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,DrainService.class).setAction(STOP),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle("油门拉满 · " + (level<0?"--":level) + "%")
            .setContentText("耗至 " + target(this) + "% 停止 · " + (remaining<0?"正在测算":duration(remaining)))
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(new Notification.Action.Builder(null,"停止耗电",stop).build()).build();
    }
    static String duration(long ms) { long m=(ms+59999)/60000; return (m/60)+"小时"+(m%60)+"分钟"; }
    private void finish(String reason) { message=reason; release(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); }
    private void release() {
        active=false; remaining=-1; gpu="GPU 待机"; handler.removeCallbacksAndMessages(null);
        if(engine!=null) { engine.stop(); engine=null; }
        if(wakeLock!=null && wakeLock.isHeld()) wakeLock.release();
    }
    @Override public void onDestroy() { if(active) message="运行已结束"; release(); if(registered) unregisterReceiver(battery); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}