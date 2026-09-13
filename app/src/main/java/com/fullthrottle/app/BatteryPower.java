package com.fullthrottle.app;

public final class BatteryPower {
    private BatteryPower() {}
    /** CURRENT_NOW is microamps, voltage broadcast is millivolts; output is battery-side watts. */
    public static double watts(int microAmps, int millivolts) {
        if(microAmps==Integer.MIN_VALUE || millivolts<=0) return Double.NaN;
        return Math.abs((double)microAmps)*millivolts/1_000_000_000.0;
    }
}