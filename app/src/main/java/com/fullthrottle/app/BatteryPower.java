package com.fullthrottle.app;

public final class BatteryPower {
    public static final int AUTO = 0, MICROAMPS = 1, MILLIAMPS = 2;
    private BatteryPower() {}
    /** This OnePlus 8T family reports mA on the verified OPlus firmware. Users can override for other ROMs. */
    public static boolean usesMilliamps(int mode, String manufacturer, String model) {
        if (mode == MILLIAMPS) return true;
        if (mode == MICROAMPS) return false;
        return "OnePlus".equalsIgnoreCase(manufacturer) && model != null
            && model.matches("(?i)KB200[01357]");
    }
    public static double amps(int rawCurrent, boolean milliamps) {
        if (rawCurrent == Integer.MIN_VALUE) return Double.NaN;
        return Math.abs((double) rawCurrent) / (milliamps ? 1000.0 : 1_000_000.0);
    }
    public static double watts(int microAmps, int millivolts) { return watts(microAmps, millivolts, false); }
    public static double watts(int rawCurrent, int millivolts, boolean milliamps) {
        if (millivolts <= 0) return Double.NaN;
        return amps(rawCurrent, milliamps) * millivolts / 1000.0;
    }
}