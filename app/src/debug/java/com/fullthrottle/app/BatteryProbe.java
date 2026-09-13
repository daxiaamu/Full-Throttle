package com.fullthrottle.app;
import android.app.Instrumentation;
import android.content.*;
import android.os.*;
public final class BatteryProbe extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Context c=getTargetContext(); BatteryManager bm=c.getSystemService(BatteryManager.class);
        Intent b=c.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        Bundle result=new Bundle();
        result.putString("stream", "\nCURRENT_NOW="+bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            +"\nCURRENT_AVERAGE="+bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
            +"\nCHARGE_COUNTER="+bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            +"\nVOLTAGE="+(b==null?0:b.getIntExtra(BatteryManager.EXTRA_VOLTAGE,0))+"\n");
        finish(0,result);
    }
}