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
    private boolean glass, night;
    private int INK, MUTED, BLUE, PALE, LINE, BACKGROUND, RED, ON_ACCENT;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView batteryText, wattsText, powerLabel, state, eta, finishAt, targetText, hardware, heat, powerDetails, coolingNotice, batteryDetails, powerUnit;
    private PowerButton toggle;
    private PopupWindow themePopup;
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
        glass=getSharedPreferences("settings",MODE_PRIVATE).getBoolean("glassTheme",false);
        night=(getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;
        INK=getColor(R.color.app_text); MUTED=getColor(R.color.app_text_secondary);
        BLUE=getColor(R.color.app_accent); PALE=getColor(R.color.app_surface);
        LINE=getColor(R.color.app_border); BACKGROUND=getColor(R.color.app_background);
        RED=getColor(R.color.app_warning); ON_ACCENT=getColor(R.color.app_on_accent);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackground(glass?new GlassDrawable(night,true,dp(28)):bg(BACKGROUND,0));
        boolean wide=getResources().getConfiguration().screenWidthDp>=600;
        boolean compact=wide && getResources().getConfiguration().screenHeightDp<500;
        LinearLayout page=new LinearLayout(this) {
            @Override protected void onMeasure(int widthSpec,int heightSpec) {
                int extra=Math.max(0,(View.MeasureSpec.getSize(widthSpec)-dp(1120))/2);
                int margin=dp(24)+extra;
                setPadding(margin,dp(compact?8:18),margin,dp(24));
                super.onMeasure(widthSpec,heightSpec);
            }
        };
        page.setOrientation(LinearLayout.VERTICAL); scroll.addView(page);
        if(Build.VERSION.SDK_INT>=30) scroll.setOnApplyWindowInsetsListener((v,insets)-> { android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()); v.setPadding(bars.left,bars.top,bars.right,bars.bottom); return insets; });
        // Older devices lay out within system bars automatically.
        if(Build.VERSION.SDK_INT<30) scroll.setOnApplyWindowInsetsListener(null);
        setContentView(scroll);
        LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text("油门拉满",compact?22:29,INK); title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView menu=text("⋯",28,INK); menu.setGravity(Gravity.CENTER); menu.setContentDescription("切换主题");
        menu.setBackground(glass?new GlassDrawable(night,false,dp(24)):bg(PALE,24));
        menu.setClickable(true); menu.setFocusable(true);
        android.util.TypedValue ripple=new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless,ripple,true);
        menu.setForeground(getDrawable(ripple.resourceId));
        header.addView(menu,new LinearLayout.LayoutParams(dp(48),dp(48)));
        liquidTouch(menu); menu.setOnClickListener(this::showThemeMenu); page.addView(header);
        if(!compact) label(page,"FULL THROTTLE  /  电池放电工具");
        gap(page,compact?8:wide?16:28);
        LinearLayout body=new LinearLayout(this), controls=column(), information=column();
        body.setOrientation(wide?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
        body.setBaselineAligned(false); body.setGravity(Gravity.TOP);
        if(wide) {
            LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-2,1.1f); left.setMarginEnd(dp(28));
            body.addView(controls,left); body.addView(information,new LinearLayout.LayoutParams(0,-2,1));
        } else {
            body.addView(controls,new LinearLayout.LayoutParams(-1,-2));
            body.addView(information,new LinearLayout.LayoutParams(-1,-2));
        }
        page.addView(body,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout metrics=column(), metricLabels=new LinearLayout(this), metricValues=new LinearLayout(this);
        TextView batteryLabel=text("剩余电量（估算）",13,MUTED);
        powerLabel=text("当前功耗 · 电池侧",13,MUTED);
        for(TextView heading:new TextView[]{batteryLabel,powerLabel}) {
            heading.setSingleLine(true); heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
            metricLabels.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        }
        batteryText=metricNumber("--.--%",INK); wattsText=metricNumber("-- W",BLUE);
        metricValues.addView(batteryText,new LinearLayout.LayoutParams(0,dp(54),1));
        metricValues.addView(wattsText,new LinearLayout.LayoutParams(0,dp(54),1));
        metricValues.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->alignMetricNumbers());
        metrics.addView(metricLabels); metrics.addView(metricValues);
        LinearLayout metricDetails=new LinearLayout(this), powerBreakdown=column();
        metricDetails.setBaselineAligned(false); metricDetails.setGravity(Gravity.TOP);
        batteryDetails=text("系统电量：--%",11,MUTED); batteryDetails.setMinHeight(dp(20));
        powerDetails=text("-- V × -- A",11,MUTED); powerUnit=text("电流单位：--",11,MUTED);
        for(TextView detail:new TextView[]{powerDetails,powerUnit}) {
            detail.setSingleLine(true); detail.setMinHeight(dp(20));
            detail.setAutoSizeTextTypeUniformWithConfiguration(9,11,1,android.util.TypedValue.COMPLEX_UNIT_SP);
            powerBreakdown.addView(detail,new LinearLayout.LayoutParams(-1,dp(20)));
        }
        metricDetails.addView(batteryDetails,new LinearLayout.LayoutParams(0,-2,1));
        metricDetails.addView(powerBreakdown,new LinearLayout.LayoutParams(0,-2,1));
        metrics.addView(metricDetails); if(glass) { metrics.setPadding(dp(16),dp(compact?4:14),dp(16),dp(compact?4:14)); metrics.setBackground(new GlassDrawable(night,false,dp(26))); } controls.addView(metrics); gap(controls,compact?4:12);
        toggle=new PowerButton(); LinearLayout.LayoutParams buttonParams=new LinearLayout.LayoutParams(dp(compact?128:wide?240:224),dp(compact?128:wide?240:224)); buttonParams.gravity=Gravity.CENTER_HORIZONTAL; controls.addView(toggle,buttonParams);
        liquidTouch(toggle); toggle.setOnClickListener(v->{ if(DrainService.active) stopService(new Intent(this,DrainService.class)); else requestStart(); update(); });
        state=text("准备就绪",16,INK); state.setGravity(Gravity.CENTER); state.setMinHeight(dp(compact?28:36)); controls.addView(state);
        coolingNotice=text("手机会发热发烫，请注意通风散热",14,RED); coolingNotice.setGravity(Gravity.CENTER); coolingNotice.setMinLines(2); coolingNotice.setVisibility(View.INVISIBLE); if(!wide) controls.addView(coolingNotice);
        hardware=text("",12,MUTED); hardware.setGravity(Gravity.CENTER); hardware.setMinHeight(dp(compact?28:36)); controls.addView(hardware); gap(controls,wide?0:18);
        LinearLayout estimate=column(); estimate.setPadding(dp(20),dp(16),dp(20),dp(16)); estimate.setBackground(glass?new GlassDrawable(night,false,dp(26)):bg(PALE,20));
        label(estimate,"距停止电量预计还需"); eta=text("开启后测算",25,INK); estimate.addView(eta); gap(estimate,6);
        finishAt=text("预计停止时间  --:--",14,MUTED); estimate.addView(finishAt); information.addView(estimate); gap(information,14);
        LinearLayout settingsRow=new LinearLayout(this); settingsRow.setGravity(Gravity.CENTER_VERTICAL); settingsRow.setPadding(dp(16),dp(12),dp(8),dp(12)); settingsRow.setBackground(glass?new GlassDrawable(night,false,dp(26)):bg(PALE,16));
        LinearLayout settingsLabels=column(); label(settingsLabels,"自动停止电量"); targetText=text("20%",23,INK); settingsLabels.addView(targetText); settingsRow.addView(settingsLabels,new LinearLayout.LayoutParams(0,-2,1));
        Button settingsButton=new Button(this); settingsButton.setText("设置"); settingsButton.setTextColor(BLUE);
        if(glass) {
            settingsButton.setBackgroundTintList(null);
            GlassDrawable settingsGlass=new GlassDrawable(night,false,dp(24));
            settingsButton.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(night?0x338AB8FF:0x22245BB3),
                settingsGlass,bg(Color.WHITE,24)));
            settingsGlass.setOwner(settingsButton);
            settingsButton.setMinWidth(dp(72)); settingsButton.setMinimumWidth(dp(72));
            settingsButton.setMinHeight(dp(48)); settingsButton.setMinimumHeight(dp(48));
            settingsButton.setPadding(dp(20),0,dp(20),0);
            settingsButton.setStateListAnimator(null);
        }
        liquidTouch(settingsButton); settingsButton.setOnClickListener(v->settings()); settingsRow.addView(settingsButton); information.addView(settingsRow); gap(information,14);
        if(wide) information.addView(coolingNotice);
        heat=text("",13,MUTED); information.addView(heat); gap(information,6);
        TextView note=text("运行时前后台均保持屏幕常亮，本页使用最高亮度。后台持续运行并显示常驻通知，可从通知停止。手动锁屏及系统管控仍由手机决定。",12,MUTED); note.setLineSpacing(dp(3),1); information.addView(note);
    }
    // Observe only: returning false preserves native click, cancellation and accessibility handling.
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private void liquidTouch(View view) {
        if(!glass) return;
        view.setOnTouchListener((v,event)-> {
            if(!android.animation.ValueAnimator.areAnimatorsEnabled()) return false;
            switch(event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().cancel();
                    v.animate().scaleX(1.035f).scaleY(.95f).setDuration(110)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().cancel();
                    v.animate().scaleX(1).scaleY(1).setDuration(320)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(1.5f)).start();
                    break;
            }
            return false;
        });
    }
    private void showThemeMenu(View anchor) {
        if(glass) { showGlassThemeMenu(anchor); return; }
        PopupMenu menu=new PopupMenu(this,anchor);
        menu.getMenu().add(1,1,0,"经典").setCheckable(true).setChecked(!glass);
        menu.getMenu().add(1,2,1,"液态玻璃").setCheckable(true).setChecked(glass);
        menu.getMenu().setGroupCheckable(1,true,true);
        menu.setOnMenuItemClickListener(item-> {
            boolean selected=item.getItemId()==2;
            if(selected!=glass) {
                getSharedPreferences("settings",MODE_PRIVATE).edit().putBoolean("glassTheme",selected).apply();
                recreate();
            }
            return true;
        });
        menu.show();
    }
    private void showGlassThemeMenu(View anchor) {
        if(themePopup!=null) themePopup.dismiss();
        LinearLayout panel=column(); panel.setPadding(dp(10),dp(10),dp(10),dp(10));
        TextView heading=text("主题",12,MUTED); heading.setPadding(dp(14),0,0,0);
        panel.addView(heading,new LinearLayout.LayoutParams(-1,dp(28)));
        RadioGroup choices=new RadioGroup(this); panel.addView(choices);
        String[] names={"经典","液态玻璃"};
        for(int i=0;i<names.length;i++) {
            final boolean selected=i==1;
            RadioButton option=new RadioButton(this);
            option.setId(View.generateViewId()); option.setText(names[i]); option.setTextSize(16);
            option.setTextColor(INK); option.setButtonTintList(android.content.res.ColorStateList.valueOf(BLUE));
            option.setPadding(dp(10),0,dp(12),0);
            option.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(night?0x338AB8FF:0x22245BB3),null,bg(Color.WHITE,18)));
            choices.addView(option,new RadioGroup.LayoutParams(-1,dp(52)));
            option.setChecked(selected);
            option.setOnClickListener(v-> {
                themePopup.dismiss();
                if(!selected) {
                    getSharedPreferences("settings",MODE_PRIVATE).edit().putBoolean("glassTheme",false).apply();
                    recreate();
                }
            });
        }
        themePopup=new PopupWindow(panel,dp(208),ViewGroup.LayoutParams.WRAP_CONTENT,true);
        GlassDrawable material=new GlassDrawable(night,false,dp(26)); material.setOwner(panel);
        themePopup.setBackgroundDrawable(material); themePopup.setElevation(dp(12));
        themePopup.setOutsideTouchable(true); themePopup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        themePopup.showAsDropDown(anchor,0,dp(8),Gravity.END);
    }
    private TextView metricNumber(String value,int color) {
        TextView view=text(value,36,color);
        view.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD));
        view.setSingleLine(true); view.setIncludeFontPadding(false);
        view.setGravity(Gravity.CENTER_VERTICAL); view.setPadding(0,0,dp(8),0);
        return view;
    }
    private void alignMetricNumbers() {
        int batteryWidth=batteryText.getWidth()-batteryText.getPaddingRight();
        int powerWidth=wattsText.getWidth()-wattsText.getPaddingRight();
        if(batteryWidth<=0 || powerWidth<=0) return;
        float maximum=android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,36,getResources().getDisplayMetrics());
        Paint measure=new Paint(batteryText.getPaint()); measure.setTextSize(maximum);
        // Reserve full-width values so live readings do not cause the font to resize.
        float size=maximum*Math.min(1f,Math.min(batteryWidth/measure.measureText("100.00%"),powerWidth/measure.measureText("999.99 W")));
        measure.setTextSize(size);
        Paint.FontMetrics font=measure.getFontMetrics();
        int height=Math.max(dp(54),(int)Math.ceil(font.bottom-font.top)+dp(12));
        for(TextView view:new TextView[]{batteryText,wattsText}) {
            if(Math.abs(view.getTextSize()-size)>0.1f) view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,size);
            if(view.getLayoutParams().height!=height) { ViewGroup.LayoutParams params=view.getLayoutParams(); params.height=height; view.setLayoutParams(params); }
        }
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
        Intent b=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED)); DrainService.sampleBattery(this,b);
        batteryText.setText(Double.isFinite(DrainService.estimatedLevel)?String.format(Locale.CHINA,"%.2f%%",DrainService.estimatedLevel):"--.--%");
        batteryDetails.setText(DrainService.level<0?"系统电量不可用":String.format(Locale.CHINA,"系统电量：%d%%",DrainService.level));
        int microAmps=getSystemService(BatteryManager.class).getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        int millivolts=b==null?0:b.getIntExtra(BatteryManager.EXTRA_VOLTAGE,0);
        int mode=getSharedPreferences("settings",MODE_PRIVATE).getInt("currentUnit",BatteryPower.AUTO);
        boolean milliamps=BatteryPower.usesMilliamps(mode,Build.MANUFACTURER,Build.MODEL);
        if(microAmps==Integer.MIN_VALUE || millivolts<=0) {
            wattsText.setText("-- W"); powerLabel.setText("电流数据暂不可用"); powerDetails.setText("-- V × -- A");
        } else {
            double watts=BatteryPower.watts(microAmps,millivolts,milliamps);
            wattsText.setText(String.format(Locale.CHINA,"%.2f W",watts));
            powerLabel.setText(DrainService.plugged?"电池净功率 · 已接电":"当前功耗 · 放电");
            powerDetails.setText(String.format(Locale.CHINA,"%.3f V × %.3f A",millivolts/1000.0,
                BatteryPower.amps(microAmps,milliamps)));
        }
        powerUnit.setText(String.format(Locale.CHINA,"电流单位：%s%s",milliamps?"mA":"µA",mode==BatteryPower.AUTO?"（自动）":"（手动）"));
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
    @Override public void onPause() { handler.removeCallbacks(refresh); if(themePopup!=null) themePopup.dismiss(); super.onPause(); }
    private class PowerButton extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final GlassDrawable lens=new GlassDrawable(night,false,dp(200));
        PowerButton() { super(MainActivity.this); setClickable(true); setFocusable(true); setBackground(glass?null:bg(BACKGROUND,112)); lens.setCallback(this); }
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) { super.onInitializeAccessibilityNodeInfo(info); info.setClassName("android.widget.Switch"); info.setCheckable(true); info.setChecked(DrainService.active); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            if(glass) { int inset=Math.round(getWidth()*30f/224); lens.setBounds(inset,inset,getWidth()-inset,getHeight()-inset); lens.draw(c); }
            float scale=Math.min(getWidth(),getHeight())/(float)dp(224);
            c.save(); c.translate((getWidth()-dp(224)*scale)/2,(getHeight()-dp(224)*scale)/2); c.scale(scale,scale);
            float cx=dp(112),cy=dp(112),r=dp(96); boolean on=DrainService.active;
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(5)); paint.setColor(LINE); c.drawCircle(cx,cy,r,paint);
            paint.setColor(on?BLUE:MUTED); paint.setStrokeCap(Paint.Cap.ROUND);
            c.drawArc(cx-r,cy-r,cx+r,cy+r,-90,(float)(Double.isFinite(DrainService.estimatedLevel)?DrainService.estimatedLevel:Math.max(0,DrainService.level))*3.6f,false,paint);
            paint.setStyle(Paint.Style.FILL); paint.setColor(on?BLUE:PALE);
            if(!glass) c.drawCircle(cx,cy,dp(82),paint);
            if(isPressed() || isFocused()) { paint.setColor(LINE); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(3)); c.drawCircle(cx,cy,dp(78),paint); }
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(5)); paint.setColor(glass?INK:on?ON_ACCENT:BLUE);
            float centerY=cy-dp(15), pr=dp(25); c.drawArc(cx-pr,centerY-pr,cx+pr,centerY+pr,-45,270,false,paint); c.drawLine(cx,centerY-dp(33),cx,centerY,paint);
            paint.setStyle(Paint.Style.FILL); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(dp(18)); paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
            c.drawText(on?"停止耗电":"开启耗电",cx,cy+dp(48),paint);
            c.restore();
        }
    }
}