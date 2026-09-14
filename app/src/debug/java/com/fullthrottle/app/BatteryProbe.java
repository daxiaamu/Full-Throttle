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
        int standard=b==null?0:b.getIntExtra(BatteryManager.EXTRA_VOLTAGE,0);
        int vendor=b==null?0:b.getIntExtra("battery_now_voltage_type",0);
        int resolved=BatteryPower.voltageMillivolts(standard,vendor);
        boolean ma=BatteryPower.usesMilliamps(BatteryPower.AUTO,Build.MANUFACTURER,Build.MODEL);
        result.putInt("vendor_millivolts",vendor);
        result.putInt("resolved_millivolts",resolved);
        result.putBoolean("auto_milliamps",ma);
        result.putDouble("resolved_watts",BatteryPower.watts(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),resolved,ma));
        BatteryTelemetry.sample(c,b,bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER));
        result.putString("unit_label",BatteryTelemetry.unitLabel());
        result.putInt("auto_voltage",BatteryTelemetry.millivolts);
        result.putDouble("auto_watts",BatteryTelemetry.watts);
        result.putInt("auto_raw",BatteryTelemetry.raw);
        finish(0,result);
    }
}