package com.fullthrottle.app;

/** Display-only estimate. Never use the decimal value to make battery cutoff decisions. */
public final class BatteryLevelEstimator {
    private static final long MAX_GAP_MS = 120_000;
    private double displayed = Double.NaN;
    private int previousLevel = -1;
    private boolean previousPlugged, counterMode;
    private long previousTime, anchorCharge, previousCharge, chargeChangedAt, rateStartTime, rateStartCharge;
    private long lastPercentEdge = -1;
    private double anchorPercent, chargePerPercent, chargeRate, percentRate;
    private boolean measuredCharge;

    public double update(long now, int level, long charge, boolean plugged) {
        if (level < 0 || level > 100) { reset(); return Double.NaN; }
        boolean validCharge = charge > 0 && charge != Integer.MIN_VALUE && level > 0;
        if (!Double.isFinite(displayed) || now < previousTime || now - previousTime > MAX_GAP_MS) {
            reset(); displayed = level; previousLevel = level; previousPlugged = plugged; previousTime = now;
            if (validCharge) anchor(now, level, charge);
            return displayed;
        }
        long elapsed = now - previousTime;
        if (elapsed == 0) return displayed;
        if (plugged != previousPlugged) {
            chargeRate = percentRate = 0; lastPercentEdge = -1;
            if (validCharge) anchor(now, level, charge);
        }
        if (level != previousLevel) {
            if (!plugged && level == previousLevel - 1) {
                if (lastPercentEdge >= 0 && now - lastPercentEdge >= 30_000)
                    percentRate = -1.0 / (now - lastPercentEdge);
                lastPercentEdge = now;
            } else {
                lastPercentEdge = -1; percentRate = 0;
            }
        }
        double target = level;
        if (validCharge) {
            boolean discontinuity = counterMode && Math.abs(charge - previousCharge) / chargePerPercent > 2.0;
            if (!counterMode || discontinuity) anchor(now, level, charge);
            if (charge != previousCharge) {
                measuredCharge = true;
                chargeChangedAt = now;
                long rateElapsed = now - rateStartTime;
                if (rateElapsed >= 5000) {
                    double observedRate = (charge - rateStartCharge) / chargePerPercent / rateElapsed;
                    chargeRate = chargeRate == 0 || Math.signum(chargeRate) != Math.signum(observedRate)
                        ? observedRate : chargeRate * 0.6 + observedRate * 0.4;
                    rateStartTime = now; rateStartCharge = charge;
                }
            }
            target = anchorPercent + (charge - anchorCharge) / chargePerPercent;
            // Reconcile a drifting counter with the actual system percentage without a visual jump.
            if (Math.abs(target - level) > 1.0) {
                anchor(now, level, charge); target = level;
            }
            // Extrapolate only a short distance beyond a measured sample, then hold.
            target += chargeRate * Math.min(Math.max(0, now - chargeChangedAt), 15_000);
            previousCharge = charge;
        } else {
            counterMode = false; chargeRate = 0; measuredCharge = false;
            if (!plugged && percentRate < 0 && lastPercentEdge >= 0)
                target = level + percentRate * Math.min(now - lastPercentEdge, 120_000);
        }
        target = Math.max(Math.max(0, level - 0.99), Math.min(Math.min(100, level + 0.99), target));
        displayed += (target - displayed) * (1 - Math.exp(-elapsed / 4000.0));
        displayed = Math.max(0, Math.min(100, displayed));
        previousLevel = level; previousPlugged = plugged; previousTime = now;
        return displayed;
    }
    public String source() {
        if (counterMode && measuredCharge) return "电荷估算";
        if (!counterMode && percentRate < 0) return "速度估算";
        return "采样中";
    }
    private void anchor(long now, int level, long charge) {
        counterMode = true; anchorCharge = previousCharge = rateStartCharge = charge;
        anchorPercent = level; chargePerPercent = charge / (double) level;
        chargeChangedAt = rateStartTime = now; chargeRate = 0; measuredCharge = false;
    }
    private void reset() {
        displayed = Double.NaN; counterMode = false; measuredCharge = false;
        chargeRate = percentRate = 0; lastPercentEdge = -1;
    }
}