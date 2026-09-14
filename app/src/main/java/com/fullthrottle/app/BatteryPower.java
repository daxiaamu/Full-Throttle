package com.fullthrottle.app;

public final class BatteryPower {
    public static final int AUTO = 0, MICROAMPS = 1, MILLIAMPS = 2;
    private BatteryPower() {}
    /** Initial compatibility profiles; runtime charge-counter evidence can override the scale. */
    public static boolean usesMilliamps(int mode, String manufacturer, String model) {
        if (mode == MILLIAMPS) return true;
        if (mode == MICROAMPS) return false;
        return ("OPPO".equalsIgnoreCase(manufacturer) && "PLG110".equalsIgnoreCase(model))
            || "OnePlus".equalsIgnoreCase(manufacturer) && model != null
            && model.matches("(?i)KB200[01357]");
    }
    /** Standard mV first; some firmware truncates that field to whole volts. */
    public static int voltageMillivolts(int standard, int vendor) {
        if (validVoltage(standard)) return standard;
        return validVoltage(vendor) ? vendor : 0;
    }
    private static boolean validVoltage(int millivolts) {
        return millivolts >= 2000 && millivolts <= 20000;
    }
    public static double amps(int rawCurrent, boolean milliamps) {
        if (rawCurrent == Integer.MIN_VALUE) return Double.NaN;
        return Math.abs((double) rawCurrent) / (milliamps ? 1000.0 : 1_000_000.0);
    }
    public static double watts(int microAmps, int millivolts) { return watts(microAmps, millivolts, false); }
    public static double watts(int rawCurrent, int millivolts, boolean milliamps) {
        if (!validVoltage(millivolts)) return Double.NaN;
        return amps(rawCurrent, milliamps) * millivolts / 1000.0;
    }
}