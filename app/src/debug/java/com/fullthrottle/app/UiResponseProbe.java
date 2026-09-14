package com.fullthrottle.app;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.accessibility.AccessibilityNodeInfo;

/** Debug-only end-to-end probe; works with classic Views and Compose semantics. */
public final class UiResponseProbe extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    private AccessibilityNodeInfo find(AccessibilityNodeInfo node,String name) {
        if(node==null) return null;
        if(name.contentEquals(node.getContentDescription()==null?"":node.getContentDescription())
            || name.contentEquals(node.getText()==null?"":node.getText())) return node;
        for(int i=0;i<node.getChildCount();i++) {
            AccessibilityNodeInfo found=find(node.getChild(i),name);
            if(found!=null) return found;
        }
        return null;
    }
    private AccessibilityNodeInfo waitNode(String name,long timeout) {
        long end=SystemClock.elapsedRealtime()+timeout;
        do {
            AccessibilityNodeInfo node=find(getUiAutomation().getRootInActiveWindow(),name);
            if(node!=null) return node;
            SystemClock.sleep(20);
        } while(SystemClock.elapsedRealtime()<end);
        throw new IllegalStateException("Missing accessible control: "+name);
    }
    private void click(String name) {
        AccessibilityNodeInfo node=waitNode(name,3000);
        android.graphics.Rect bounds=new android.graphics.Rect();
        node.getBoundsInScreen(bounds);
        if(bounds.isEmpty()) throw new IllegalStateException("Empty control: "+name);
        // Physical input also verifies the Compose overlay's hit testing.
        long down=SystemClock.uptimeMillis();
        android.view.MotionEvent press=android.view.MotionEvent.obtain(down,down,0,bounds.centerX(),bounds.centerY(),0);
        android.view.MotionEvent release=android.view.MotionEvent.obtain(down,down+25,1,bounds.centerX(),bounds.centerY(),0);
        try {
            getUiAutomation().injectInputEvent(press,true);
            SystemClock.sleep(25);
            getUiAutomation().injectInputEvent(release,true);
        } finally { press.recycle(); release.recycle(); }
    }
    private void waitMenuClosed() {
        long end=SystemClock.elapsedRealtime()+3000;
        while(find(getUiAutomation().getRootInActiveWindow(),"经典")!=null) {
            if(SystemClock.elapsedRealtime()>end) throw new IllegalStateException("Menu did not close");
            SystemClock.sleep(50);
        }
    }
    @Override public void onStart() {
        Bundle result=new Bundle();
        MainActivity activity=null;
        android.content.SharedPreferences prefs=getTargetContext().getSharedPreferences("settings",0);
        int originalTarget=prefs.getInt("target",20),originalUnit=prefs.getInt("currentUnit",0);
        try {
            activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitNode("切换主题",5000);
            long time=SystemClock.elapsedRealtime(); click("切换主题"); waitNode("经典",3000);
            result.putLong("menu_visible_ms",SystemClock.elapsedRealtime()-time);
            result.putBoolean("menu_visible",true);
            SystemClock.sleep(250); click("液态玻璃"); waitMenuClosed(); SystemClock.sleep(100);
            result.putLong("heap_before_bytes",Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory());
            time=SystemClock.elapsedRealtime(); click("开启耗电"); waitNode("停止耗电",3000);
            result.putLong("start_to_stop_control_ms",SystemClock.elapsedRealtime()-time);
            boolean[] active={false}; runOnMainSync(()->active[0]=DrainService.active);
            result.putBoolean("started",active[0]);
            // Keep real CPU/GPU load active briefly and verify menu remains operable.
            SystemClock.sleep(1800);
            result.putLong("heap_running_bytes",Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory());
            time=SystemClock.elapsedRealtime(); click("切换主题"); waitNode("经典",3000);
            result.putLong("loaded_menu_visible_ms",SystemClock.elapsedRealtime()-time);
            SystemClock.sleep(250); click("液态玻璃"); waitMenuClosed(); SystemClock.sleep(100);
            time=SystemClock.elapsedRealtime(); click("停止耗电");
            SystemClock.sleep(300); runOnMainSync(()->result.putBoolean("active_after_stop",DrainService.active)); waitNode("开启耗电",3000);
            result.putLong("stop_to_start_control_ms",SystemClock.elapsedRealtime()-time);
            result.putLong("heap_stopped_bytes",Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory());
            click("耗电设置"); waitNode("保存",3000);
            AccessibilityNodeInfo slider=waitNode("停止电量，1% 至 100%",3000);
            Bundle progress=new Bundle(); progress.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,20);
            if(!slider.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),progress))
                throw new IllegalStateException("Slider accessibility action failed");
             click("保存"); SystemClock.sleep(200);
            result.putBoolean("settings_saved",prefs.getInt("target",0)==21);
            if(!result.getBoolean("settings_saved")) throw new IllegalStateException("Settings did not persist");
            click("切换主题"); waitNode("经典",3000); SystemClock.sleep(250); click("经典");
            SystemClock.sleep(500); waitNode("切换主题",3000);
            result.putBoolean("classic_selected",!prefs.getBoolean("glassTheme",true));
            click("切换主题"); waitNode("液态玻璃",3000); SystemClock.sleep(250); click("液态玻璃");
            SystemClock.sleep(500); waitNode("切换主题",3000);
            result.putBoolean("glass_restored",prefs.getBoolean("glassTheme",false));
        } catch(Exception error) { result.putString("error",error.toString()); }
        finally {
            prefs.edit().putInt("target",originalTarget).putInt("currentUnit",originalUnit).apply();
            getTargetContext().stopService(new Intent(getTargetContext(),DrainService.class));
            SystemClock.sleep(100);
            runOnMainSync(()->result.putBoolean("stopped",!DrainService.active));
            if(activity!=null) { MainActivity screen=activity; runOnMainSync(screen::finish); }
        }
        finish(result.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);
    }
}