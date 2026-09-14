package com.fullthrottle.app;

import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;
import android.os.Build;
import android.os.SystemClock;

/** Shared, main-thread sampling keeps both themes and background verification consistent. */
final class BatteryTelemetry {
    private static final boolean profileMa=BatteryPower.usesMilliamps(BatteryPower.AUTO,Build.MANUFACTURER,Build.MODEL);
    private static final CurrentUnitDetector detector=new CurrentUnitDetector(
        profileMa?BatteryPower.MILLIAMPS:BatteryPower.MICROAMPS);
    static int raw=Integer.MIN_VALUE, millivolts, unit;
    static double amps=Double.NaN, watts=Double.NaN;
    static void sample(Context context, Intent battery, long charge) {
        raw=context.getSystemService(BatteryManager.class).getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        millivolts=battery==null?0:BatteryPower.voltageMillivolts(
            battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE,0),
            battery.getIntExtra("battery_now_voltage_type",0));
        detector.sample(SystemClock.elapsedRealtime(),charge,raw,
            battery!=null && battery.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0);
        unit=detector.unit();
        amps=unit==BatteryPower.AUTO?Double.NaN:BatteryPower.amps(raw,unit==BatteryPower.MILLIAMPS);
        if(amps>30) amps=Double.NaN;
        watts=millivolts>0?amps*millivolts/1000.0:Double.NaN;
    }
    static boolean valid() { return Double.isFinite(watts); }
    static String unitLabel() {
        if(unit==BatteryPower.AUTO) return "电流单位：自动核验中";
        return "电流单位："+(unit==BatteryPower.MILLIAMPS?"mA":"µA")
            +(detector.verified()?"（已核验）":profileMa?"（自动兼容）":"（系统标准）");
    }
}