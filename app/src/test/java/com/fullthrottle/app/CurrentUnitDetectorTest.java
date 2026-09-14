package com.fullthrottle.app;
import org.junit.Test;
import static org.junit.Assert.*;

public class CurrentUnitDetectorTest {
    private void sample(CurrentUnitDetector d,int raw,int dropPerSecond,int seconds) {
        for(int i=0;i<=seconds;i++) d.sample(i*1000L,4_000_000L-i*dropPerSecond,raw,false);
    }
    @Test public void milliampFirmwareNeedsTwoIndependentWindows() {
        CurrentUnitDetector d=new CurrentUnitDetector(BatteryPower.MICROAMPS);
        for(int i=0;i<=30;i++) d.sample(i*1000L,4_000_000L-i*1000,-3600,false);
        assertEquals(BatteryPower.AUTO,d.unit());
        assertFalse(d.verified());
        for(int i=31;i<=60;i++) d.sample(i*1000L,4_000_000L-i*1000,-3600,false);
        assertEquals(BatteryPower.MILLIAMPS,d.unit()); assertTrue(d.verified());
    }
    @Test public void standardFirmwareOverridesObsoleteCompatibilityProfile() {
        CurrentUnitDetector d=new CurrentUnitDetector(BatteryPower.MILLIAMPS);
        sample(d,-3_600_000,1000,60);
        assertEquals(BatteryPower.MICROAMPS,d.unit()); assertTrue(d.verified());
    }
    @Test public void lowCurrentAloneDoesNotImplyMilliamps() {
        CurrentUnitDetector d=new CurrentUnitDetector(BatteryPower.MICROAMPS);
        sample(d,500,0,120);
        assertEquals(BatteryPower.MICROAMPS,d.unit()); assertFalse(d.verified());
    }
    @Test public void inconsistentCounterDoesNotInventScale() {
        CurrentUnitDetector d=new CurrentUnitDetector(BatteryPower.MICROAMPS);
        sample(d,3600,100,60);
        assertEquals(BatteryPower.AUTO,d.unit()); assertFalse(d.verified());
    }
    @Test public void duplicateSamplesDoNotAccelerateConfirmation() {
        CurrentUnitDetector d=new CurrentUnitDetector(BatteryPower.MICROAMPS);
        for(int i=0;i<=30;i++) for(int j=0;j<10;j++) d.sample(i*1000L,4_000_000L-i*1000,-3600,false);
        assertFalse(d.verified());
    }
    @Test public void gapPlugAndDirectionChangesDiscardPendingEvidence() {
        for(int reset=0;reset<3;reset++) {
            CurrentUnitDetector d=new CurrentUnitDetector(BatteryPower.MICROAMPS);
            sample(d,-3600,1000,30);
            for(int i=31;i<=61;i++) d.sample((i+(reset==0?10:0))*1000L,
                4_000_000L-i*1000,reset==2?3600:-3600,reset==1);
            assertFalse(d.verified());
        }
    }
    @Test public void missingSamplesAndHugeCounterJumpCannotVerify() {
        CurrentUnitDetector d=new CurrentUnitDetector(BatteryPower.MICROAMPS);
        sample(d,Integer.MIN_VALUE,1000,90);
        assertFalse(d.verified());
        d=new CurrentUnitDetector(BatteryPower.MICROAMPS);
        sample(d,-3600,100000,60);
        assertFalse(d.verified()); assertEquals(BatteryPower.AUTO,d.unit());
    }
    @Test public void equivalentCapacityFactorDoesNotChangeUnit() {
        CurrentUnitDetector d=new CurrentUnitDetector(BatteryPower.MICROAMPS);
        sample(d,-3600,2000,60);
        assertEquals(BatteryPower.MILLIAMPS,d.unit());
    }
}