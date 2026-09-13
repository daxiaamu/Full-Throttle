package com.fullthrottle.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class BatteryLevelEstimatorTest {
    private static final long MISSING = Integer.MIN_VALUE;
    @Test public void startsAtSystemValueWithoutInventingDecimals() {
        BatteryLevelEstimator e = new BatteryLevelEstimator();
        assertEquals(87.0,e.update(0,87,4_350_000,false),0);
        assertEquals(87.0,e.update(1000,87,4_350_000,false),0);
        assertEquals("采样中",e.source());
    }
    @Test public void chargeLossMovesDisplayBeforeSystemPercentageChanges() {
        BatteryLevelEstimator e = new BatteryLevelEstimator();
        e.update(0,87,4_350_000,false);
        double value=e.update(5000,87,4_345_000,false);
        assertTrue(value < 86.99 && value > 86.90);
        assertEquals("电荷估算",e.source());
        double next=e.update(6000,87,4_345_000,false);
        assertTrue(next < value);
    }
    @Test public void commonCounterScaleCancelsWithoutAssumingCellCount() {
        BatteryLevelEstimator a=new BatteryLevelEstimator(), b=new BatteryLevelEstimator();
        a.update(0,80,4_000_000,false); b.update(0,80,2_000_000,false);
        assertEquals(a.update(5000,80,3_995_000,false),b.update(5000,80,1_997_500,false),0.000001);
    }
    @Test public void missingCounterWaitsForTwoPercentTransitions() {
        BatteryLevelEstimator e=new BatteryLevelEstimator(); e.update(0,80,MISSING,false);
        assertEquals(80,e.update(30000,80,MISSING,false),0);
        e.update(60000,79,MISSING,false);
        assertEquals("采样中",e.source());
        e.update(120000,78,MISSING,false);
        double value=e.update(150000,78,MISSING,false);
        assertTrue(value<78 && value>77);
        assertEquals("速度估算",e.source());
    }
    @Test public void chargeExtrapolationStopsAfterShortHorizon() {
        BatteryLevelEstimator e=new BatteryLevelEstimator(); e.update(0,80,4_000_000,false);
        e.update(5000,80,3_995_000,false);
        double value=0;
        for(long t=6000;t<=90000;t+=1000) value=e.update(t,80,3_995_000,false);
        assertEquals(79.6,value,0.00001);
    }
    @Test public void connectingPowerClearsOldDischargePrediction() {
        BatteryLevelEstimator e=new BatteryLevelEstimator(); e.update(0,80,4_000_000,false);
        e.update(5000,80,3_995_000,false);
        double connected=e.update(6000,80,3_995_000,true);
        double later=e.update(10000,80,3_995_000,true);
        assertTrue(later>=connected);
        assertEquals("采样中",e.source());
    }
    @Test public void resumedAfterLongGapReanchorsImmediately() {
        BatteryLevelEstimator e=new BatteryLevelEstimator(); e.update(0,80,4_000_000,false);
        e.update(5000,80,3_995_000,false);
        assertEquals(70,e.update(200000,70,3_500_000,false),0);
    }
    @Test public void largeCounterJumpDoesNotBecomeRapidDischarge() {
        BatteryLevelEstimator e=new BatteryLevelEstimator(); e.update(0,80,4_000_000,false);
        assertEquals(80,e.update(5000,80,2_000_000,false),0);
        assertEquals(80,e.update(10000,80,2_000_000,false),0);
    }
    @Test public void invalidReadingClearsEstimateAndCanRecover() {
        BatteryLevelEstimator e=new BatteryLevelEstimator(); e.update(0,80,4_000_000,false);
        assertTrue(Double.isNaN(e.update(1000,-1,MISSING,false)));
        assertEquals(79,e.update(2000,79,3_950_000,false),0);
    }
    @Test public void zeroAndFullRemainWithinPhysicalBounds() {
        BatteryLevelEstimator e=new BatteryLevelEstimator();
        assertEquals(0,e.update(0,0,0,false),0);
        e=new BatteryLevelEstimator(); e.update(0,100,5_000_000,true);
        assertEquals(100,e.update(5000,100,5_005_000,true),0);
    }
    @Test public void systemCorrectionIsSmoothAndConverges() {
        BatteryLevelEstimator e=new BatteryLevelEstimator(); e.update(0,80,4_000_000,false);
        double corrected=e.update(1000,78,4_000_000,false);
        assertTrue(corrected<80 && corrected>78);
        for(long t=2000;t<=30000;t+=1000) corrected=e.update(t,78,4_000_000,false);
        assertEquals(78,corrected,0.01);
    }
}