package com.fullthrottle.app;

/** Compares integrated raw current against charge-counter movement, never raw magnitude alone. */
public final class CurrentUnitDetector {
    private final int fallback;
    private int verified, candidate, confirmations;
    private boolean conflicted, started, connected;
    private long start, previousTime, initialCharge;
    private double integral, previousCurrent;
    private int samples;
    public CurrentUnitDetector(int fallback) { this.fallback=fallback; }
    public int unit() { return conflicted ? BatteryPower.AUTO : verified!=0 ? verified : fallback; }
    public boolean verified() { return verified!=0 && !conflicted; }
    private void begin(long time,long charge,int raw,boolean plugged) {
        started=true; start=previousTime=time; initialCharge=charge;
        previousCurrent=raw; connected=plugged; integral=0; samples=0;
    }
    public void sample(long time,long charge,int raw,boolean plugged) {
        if(charge<=0 || raw==Integer.MIN_VALUE) {
            started=false; candidate=confirmations=0; return;
        }
        if(!started || time<previousTime || time-previousTime>5000 || connected!=plugged
                || (raw!=0 && previousCurrent!=0 && Math.signum(raw)!=Math.signum(previousCurrent))) {
            candidate=confirmations=0; begin(time,charge,raw,plugged); return;
        }
        long dt=time-previousTime;
        if(dt<500) return; // UI and foreground service may sample the same instant.
        integral+=(Math.abs(previousCurrent)+Math.abs((double)raw))*0.5*dt;
        previousCurrent=raw; previousTime=time; samples++;
        long duration=time-start;
        double movement=Math.abs((double)charge-initialCharge);
        if(duration<30000 || samples<15 || movement<2000) {
            if(duration>=120000) { candidate=confirmations=0; begin(time,charge,raw,plugged); }
            return;
        }
        double meanMicroamps=movement*3600000.0/duration;
        double meanRaw=integral/duration;
        double ratio=meanMicroamps/meanRaw;
        int evidence=0;
        // Allow gauge quantization and single-cell/equivalent-capacity differences, but
        // leave a wide rejection gap between scales which differ by a factor of 1000.
        if(meanMicroamps>=10000 && meanMicroamps<=30000000 && meanRaw>0) {
            if(ratio>=0.25 && ratio<=4) evidence=BatteryPower.MICROAMPS;
            else if(ratio>=250 && ratio<=4000) evidence=BatteryPower.MILLIAMPS;
        }
        int current=unit();
        if(evidence==0 || evidence!=current) conflicted=true;
        if(evidence!=0 && evidence==candidate) confirmations++;
        else { candidate=evidence; confirmations=evidence==0?0:1; }
        if(confirmations>=2) { verified=evidence; conflicted=false; }
        begin(time,charge,raw,plugged);
    }
}