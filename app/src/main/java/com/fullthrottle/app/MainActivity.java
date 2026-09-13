package com.fullthrottle.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private static final int INK=0xff172c49, MUTED=0xff586a80, BLUE=0xff245bb3, PALE=0xffedf3fa, LINE=0xffdbe5f0, WHITE=0xffffffff, RED=0xffb43d30;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView batteryText, wattsText, powerLabel, state, eta, finishAt, targetText, hardware, heat, powerDetails, coolingNotice;
    private PowerButton toggle;
    private final Runnable refresh=new Runnable() { public void run() { update(); handler.postDelayed(this,1000); }};
    private int dp(float n) { return (int)(getResources().getDisplayMetrics().density*n+0.5f); }
    private TextView text(String value,int size,int color) {
        TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setGravity(Gravity.CENTER_VERTICAL); return t;
    }
    private GradientDrawable bg(int color,int radius) { GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private LinearLayout column() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private void gap(LinearLayout l,int h) { l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(h))); }
    private TextView label(LinearLayout box,String title) { TextView t=text(title,13,MUTED); box.addView(t); return t; }
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(WHITE);
        LinearLayout page=column(); page.setPadding(dp(24),dp(18),dp(24),dp(24)); scroll.addView(page);
        if(Build.VERSION.SDK_INT>=30) scroll.setOnApplyWindowInsetsListener((v,insets)-> { android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()); v.setPadding(bars.left,bars.top,bars.right,bars.bottom); return insets; });
        // Older devices lay out within system bars automatically.
        if(Build.VERSION.SDK_INT<30) scroll.setOnApplyWindowInsetsListener(null);
        setContentView(scroll);
        TextView title=text("油门拉满",29,INK); title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); page.addView(title);
        label(page,"FULL THROTTLE  /  电池放电工具"); gap(page,28);
        LinearLayout metrics=new LinearLayout(this);
        LinearLayout batteryBox=column(), powerBox=column();
        label(batteryBox,"剩余电量"); batteryText=text("--%",42,INK); batteryText.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD)); batteryBox.addView(batteryText);
        powerLabel=label(powerBox,"当前功耗 · 电池侧"); wattsText=text("-- W",34,BLUE); wattsText.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD)); powerBox.addView(wattsText);
        metrics.addView(batteryBox,new LinearLayout.LayoutParams(0,-2,1)); metrics.addView(powerBox,new LinearLayout.LayoutParams(0,-2,1)); page.addView(metrics); powerDetails=text("",12,MUTED); page.addView(powerDetails); gap(page,12);
        toggle=new PowerButton(); LinearLayout.LayoutParams buttonParams=new LinearLayout.LayoutParams(dp(224),dp(224)); buttonParams.gravity=Gravity.CENTER_HORIZONTAL; page.addView(toggle,buttonParams);
        toggle.setOnClickListener(v->{ if(DrainService.active) stopService(new Intent(this,DrainService.class)); else requestStart(); update(); });
        state=text("准备就绪",16,INK); state.setGravity(Gravity.CENTER); state.setMinHeight(dp(36)); page.addView(state);
        coolingNotice=text("手机会发热发烫，请注意通风散热",14,RED); coolingNotice.setGravity(Gravity.CENTER); coolingNotice.setMinLines(2); coolingNotice.setVisibility(View.INVISIBLE); page.addView(coolingNotice);
        hardware=text("",12,MUTED); hardware.setGravity(Gravity.CENTER); hardware.setMinHeight(dp(36)); page.addView(hardware); gap(page,18);
        LinearLayout estimate=column(); estimate.setPadding(dp(20),dp(16),dp(20),dp(16)); estimate.setBackground(bg(PALE,20));
        label(estimate,"距停止电量预计还需"); eta=text("开启后测算",25,INK); estimate.addView(eta); gap(estimate,6);
        finishAt=text("预计停止时间  --:--",14,MUTED); estimate.addView(finishAt); page.addView(estimate); gap(page,14);
        LinearLayout settingsRow=new LinearLayout(this); settingsRow.setGravity(Gravity.CENTER_VERTICAL); settingsRow.setPadding(dp(16),dp(12),dp(8),dp(12)); settingsRow.setBackground(bg(PALE,16));
        LinearLayout settingsLabels=column(); label(settingsLabels,"自动停止电量"); targetText=text("20%",23,INK); settingsLabels.addView(targetText); settingsRow.addView(settingsLabels,new LinearLayout.LayoutParams(0,-2,1));
        Button settingsButton=new Button(this); settingsButton.setText("设置"); settingsButton.setTextColor(BLUE); settingsButton.setOnClickListener(v->settings()); settingsRow.addView(settingsButton); page.addView(settingsRow); gap(page,14);
        heat=text("",13,MUTED); page.addView(heat); gap(page,6);
        TextView note=text("开启时本页保持最亮、常亮。切至后台后 CPU / GPU 持续运行，可从通知停止。频率由系统调度；电池达到 50°C 时自动停止。",12,MUTED); note.setLineSpacing(dp(3),1); page.addView(note);
    }
    private void requestStart() {
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},7); return;
        }
        NotificationManager nm=getSystemService(NotificationManager.class);
        NotificationChannel channel=nm.getNotificationChannel(DrainService.CHANNEL);
        if(!nm.areNotificationsEnabled() || (channel!=null && channel.getImportance()==NotificationManager.IMPORTANCE_NONE)) {
            new AlertDialog.Builder(this).setTitle("请开启运行通知").setMessage("通知会显示耗电状态，并提供停止按钮。开启通知后再点击主开关。")
                .setPositiveButton("通知设置",(d,w)->startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName())))
                .setNegativeButton("取消",null).show(); return;
        }
        try { startForegroundService(new Intent(this,DrainService.class)); }
        catch(RuntimeException e) { Toast.makeText(this,"无法启动，请保持应用在前台后重试",Toast.LENGTH_LONG).show(); }
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(code,permissions,results);
        if(code==7 && results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED) requestStart();
        else if(code==7) Toast.makeText(this,"需要开启通知，才能在状态栏控制耗电",Toast.LENGTH_LONG).show();
    }
    private void settings() {
        LinearLayout content=column(); content.setPadding(dp(24),dp(10),dp(24),dp(8));
        int selected=DrainService.target(this); TextView value=text(selected+"%",32,INK); content.addView(value);
        SeekBar slider=new SeekBar(this); slider.setMax(99); slider.setProgress(selected-1); content.addView(slider,new LinearLayout.LayoutParams(-1,dp(52)));
        slider.setContentDescription("停止电量，1% 至 100%");
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s,int p,boolean fromUser) { value.setText((p+1)+"%"); }
            public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {}
        });
        TextView hint=text("剩余电量低于或等于此值时停止。设置立即生效。",14,MUTED); content.addView(hint);
        gap(content,16);
        label(content,"电流单位（影响功耗显示）");
        RadioGroup units=new RadioGroup(this); int[] unitIds={View.generateViewId(),View.generateViewId(),View.generateViewId()};
        boolean autoMa=BatteryPower.usesMilliamps(BatteryPower.AUTO,Build.MANUFACTURER,Build.MODEL);
        String[] unitNames={"自动（本机使用 "+(autoMa?"mA":"µA")+"）","µA · Android 标准","mA · 部分厂商系统"};
        for(int i=0;i<unitNames.length;i++) { RadioButton option=new RadioButton(this); option.setId(unitIds[i]); option.setText(unitNames[i]); units.addView(option); }
        units.check(unitIds[Math.max(0,Math.min(2,getSharedPreferences("settings",MODE_PRIVATE).getInt("currentUnit",BatteryPower.AUTO)))]);
        content.addView(units);
        TextView unitHint=text("一加 8T 默认按 mA 换算；其他 ROM 可手动切换。双电芯不自动乘 2。",12,MUTED); content.addView(unitHint);
        ScrollView settingsScroll=new ScrollView(this); settingsScroll.addView(content);
        new AlertDialog.Builder(this).setTitle("耗电设置").setView(settingsScroll).setNegativeButton("取消",null)
            .setPositiveButton("保存",(dialog,which)-> {
                int target=slider.getProgress()+1;
                getSharedPreferences("settings",MODE_PRIVATE).edit().putInt("target",target).putInt("currentUnit",units.indexOfChild(units.findViewById(units.getCheckedRadioButtonId()))).apply();
                if(DrainService.active) startService(new Intent(this,DrainService.class)); update();
            }).show();
    }
    private void update() {
        Intent b=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED)); DrainService.readBattery(b);
        batteryText.setText(DrainService.level<0?"--%":DrainService.level+"%");
        int microAmps=getSystemService(BatteryManager.class).getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        int millivolts=b==null?0:b.getIntExtra(BatteryManager.EXTRA_VOLTAGE,0);
        int mode=getSharedPreferences("settings",MODE_PRIVATE).getInt("currentUnit",BatteryPower.AUTO);
        boolean milliamps=BatteryPower.usesMilliamps(mode,Build.MANUFACTURER,Build.MODEL);
        if(microAmps==Integer.MIN_VALUE || millivolts<=0) {
            wattsText.setText("-- W"); powerLabel.setText("电流数据暂不可用"); powerDetails.setText("系统未提供有效的电流或电压");
        } else {
            double watts=BatteryPower.watts(microAmps,millivolts,milliamps);
            wattsText.setText(String.format(Locale.CHINA,"%.2f W",watts));
            powerLabel.setText(DrainService.plugged?"电池净功率 · 已接电":"当前功耗 · 放电");
            powerDetails.setText(String.format(Locale.CHINA,"%.3f V × %.3f A · 电流单位 %s%s",millivolts/1000.0,
                BatteryPower.amps(microAmps,milliamps),milliamps?"mA":"µA",mode==BatteryPower.AUTO?"（自动）":"（手动）"));
        }
        boolean running=DrainService.active;
        coolingNotice.setVisibility(running?View.VISIBLE:View.INVISIBLE);
        state.setText(DrainService.message); state.setTextColor(running?BLUE:INK);
        hardware.setText(running?"CPU "+Runtime.getRuntime().availableProcessors()+" 线程  /  "+DrainService.gpu:"CPU / GPU 待机");
        targetText.setText(DrainService.target(this)+"%");
        long remaining=DrainService.remaining;
        eta.setText(!running?"开启后测算":DrainService.plugged?"接通电源，暂停预测":remaining<0?"正在采样…":DrainService.duration(remaining));
        if(running && !DrainService.plugged && remaining>=0) {
            Date time=new Date(System.currentTimeMillis()+remaining);
            finishAt.setText("预计停止于 "+new SimpleDateFormat("MM月dd日 HH:mm",Locale.CHINA).format(time));
        } else finishAt.setText(running && !DrainService.plugged?"根据实际掉电速度计算，通常需 1–3 分钟":"预计停止时间  --:--");
        heat.setText(String.format(Locale.CHINA,"电池温度 %.1f°C  ·  %s",DrainService.temperature,DrainService.plugged?"已连接电源":"使用电池"));
        WindowManager.LayoutParams params=getWindow().getAttributes(); float brightness=running?1f:WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        if(params.screenBrightness!=brightness) { params.screenBrightness=brightness; getWindow().setAttributes(params); }
        if(running) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        toggle.setContentDescription(running?"停止耗电":"开启耗电"); toggle.invalidate();
    }
    @Override public void onResume() { super.onResume(); handler.post(refresh); }
    @Override public void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
    private class PowerButton extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        PowerButton() { super(MainActivity.this); setClickable(true); setFocusable(true); setBackground(bg(WHITE,112)); }
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) { super.onInitializeAccessibilityNodeInfo(info); info.setClassName("android.widget.Switch"); info.setCheckable(true); info.setChecked(DrainService.active); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c); float cx=getWidth()/2f,cy=getHeight()/2f,r=dp(96); boolean on=DrainService.active;
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(5)); paint.setColor(LINE); c.drawCircle(cx,cy,r,paint);
            paint.setColor(on?BLUE:MUTED); paint.setStrokeCap(Paint.Cap.ROUND);
            c.drawArc(cx-r,cy-r,cx+r,cy+r,-90,Math.max(0,DrainService.level)*3.6f,false,paint);
            paint.setStyle(Paint.Style.FILL); paint.setColor(on?BLUE:PALE); c.drawCircle(cx,cy,dp(82),paint);
            if(isPressed() || isFocused()) { paint.setColor(LINE); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(3)); c.drawCircle(cx,cy,dp(78),paint); }
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(5)); paint.setColor(on?WHITE:BLUE);
            float centerY=cy-dp(15), pr=dp(25); c.drawArc(cx-pr,centerY-pr,cx+pr,centerY+pr,-45,270,false,paint); c.drawLine(cx,centerY-dp(33),cx,centerY,paint);
            paint.setStyle(Paint.Style.FILL); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(dp(18)); paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
            c.drawText(on?"停止耗电":"开启耗电",cx,cy+dp(48),paint);
        }
    }
}