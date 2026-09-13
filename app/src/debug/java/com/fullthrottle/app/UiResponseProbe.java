package com.fullthrottle.app;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.*;
import android.widget.PopupWindow;

/** Debug-only regression probe for the two reported slow click paths. */
public final class UiResponseProbe extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    private View find(View view,String description) {
        if(description.contentEquals(view.getContentDescription()==null?"":view.getContentDescription())) return view;
        if(view instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++) { View found=find(group.getChildAt(i),description); if(found!=null) return found; }
        }
        return null;
    }
    @Override public void onStart() {
        Bundle result=new Bundle();
        MainActivity activity=null;
        try {
            activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            MainActivity screen=activity;
            // Allow initial material work to settle; click handlers themselves are timed on main.
            SystemClock.sleep(500);
            runOnMainSync(()-> {
                View menu=find(screen.getWindow().getDecorView(),"切换主题");
                long start=SystemClock.elapsedRealtimeNanos(); menu.performClick();
                result.putDouble("menu_handler_ms",(SystemClock.elapsedRealtimeNanos()-start)/1000000.0);
                try {
                    java.lang.reflect.Field field=MainActivity.class.getDeclaredField("themePopup"); field.setAccessible(true);
                    PopupWindow popup=(PopupWindow)field.get(screen);
                    result.putBoolean("menu_visible",popup!=null && popup.isShowing());
                    if(popup!=null) popup.dismiss();
                } catch(Exception error) { throw new RuntimeException(error); }
            });
            long request=SystemClock.elapsedRealtime();
            runOnMainSync(()-> {
                View startButton=find(screen.getWindow().getDecorView(),"开启耗电");
                if(startButton==null) throw new IllegalStateException("Start button unavailable");
                long start=SystemClock.elapsedRealtimeNanos(); startButton.performClick();
                result.putDouble("start_handler_ms",(SystemClock.elapsedRealtimeNanos()-start)/1000000.0);
            });
            boolean[] active={false};
            while(SystemClock.elapsedRealtime()-request<3000) {
                runOnMainSync(()->active[0]=DrainService.active);
                if(active[0]) break;
                SystemClock.sleep(10);
            }
            result.putBoolean("started",active[0]);
            result.putLong("start_observed_ms",SystemClock.elapsedRealtime()-request);
            runOnMainSync(()-> {
                View stop=find(screen.getWindow().getDecorView(),"停止耗电");
                result.putBoolean("stop_button_ready",stop!=null);
                if(stop!=null) {
                    long start=SystemClock.elapsedRealtimeNanos(); stop.performClick();
                    result.putDouble("stop_handler_ms",(SystemClock.elapsedRealtimeNanos()-start)/1000000.0);
                }
            });
        } catch(Exception error) { result.putString("error",error.toString()); }
        finally {
            getTargetContext().stopService(new Intent(getTargetContext(),DrainService.class));
            SystemClock.sleep(100);
            runOnMainSync(()->result.putBoolean("stopped",!DrainService.active));
            if(activity!=null) { MainActivity screen=activity; runOnMainSync(screen::finish); }
        }
        finish(result.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);
    }
}