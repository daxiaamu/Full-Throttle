package com.fullthrottle.app;

import java.util.ArrayDeque;

/** Uses measured charge loss; falls back to percentage transitions on unsupported devices. */
public final class DischargeEstimator {
    private record Sample(long time, int level, long charge) {}
    private final ArrayDeque<Sample> samples = new ArrayDeque<>();
    public void reset() { samples.clear(); }
    public long update(long now, int level, long charge, boolean plugged, int target) {
        if (plugged || level < 0) { reset(); return -1; }
        if (level <= target) return 0;
        if (!samples.isEmpty() && level > samples.peekLast().level) reset();
        if (samples.isEmpty() || now - samples.peekLast().time >= 5000)
            samples.addLast(new Sample(now, level, charge));
        while (samples.size() > 2 && now - samples.peekFirst().time > 900000) samples.removeFirst();
        Sample first = samples.peekFirst();
        long elapsed = now - first.time;
        if (elapsed < 60000) return -1;
        double remaining;
        if (charge > 0 && first.charge > charge && first.charge - charge >= 1000) {
            double fullCharge = charge * 100.0 / level;
            remaining = (charge - fullCharge * target / 100.0) * elapsed / (first.charge - charge);
        } else {
            // Two transitions reduce quantization error from starting between percentage boundaries.
            int drop = first.level - level;
            if (drop < 2) return -1;
            remaining = (level - target) * (double) elapsed / drop;
        }
        return Double.isFinite(remaining) && remaining >= 0 ? (long) Math.min(remaining, 604800000L) : -1;
    }
}