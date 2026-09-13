package com.fullthrottle.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class BatteryLogicTest {
    @Test public void onePlus8tMilliampsProducesWattsInsteadOfRoundingToZero() {
        assertTrue(BatteryPower.usesMilliamps(BatteryPower.AUTO,"OnePlus","KB2000"));
        assertEquals(2.5823, BatteryPower.watts(620,4165,true),0.000001);
        assertEquals(2.5823, BatteryPower.watts(-620,4165,true),0.000001);
    }
    @Test public void explicitUnitOverridesDeviceProfile() {
        assertFalse(BatteryPower.usesMilliamps(BatteryPower.MICROAMPS,"OnePlus","KB2000"));
        assertTrue(BatteryPower.usesMilliamps(BatteryPower.MILLIAMPS,"Other","Other"));
        assertEquals(2.5823,BatteryPower.watts(620000,4165,false),0.000001);
    }
    @Test public void otherDevicesKeepAndroidStandardUnits() {
        assertFalse(BatteryPower.usesMilliamps(BatteryPower.AUTO,"Google","Pixel"));
        assertFalse(BatteryPower.usesMilliamps(BatteryPower.AUTO,"OnePlus","Other"));
        assertFalse(BatteryPower.usesMilliamps(BatteryPower.AUTO,"Other",null));
    }
    @Test public void missingAndZeroCurrentAreNotConfusedInMilliampMode() {
        assertTrue(Double.isNaN(BatteryPower.watts(Integer.MIN_VALUE,4165,true)));
        assertEquals(0.0,BatteryPower.watts(0,4165,true),0.0);
    }
    @Test public void wattsConvertsBothUnitsWithoutIntegerOverflow() {
        assertEquals(8.0, BatteryPower.watts(-2_000_000,4000),0.0001);
        assertEquals(8.0, BatteryPower.watts(2_000_000,4000),0.0001);
        assertTrue(Double.isNaN(BatteryPower.watts(Integer.MIN_VALUE,4000)));
        assertTrue(Double.isNaN(BatteryPower.watts(1000,0)));
    }
    @Test public void noInventedEstimateBeforeSufficientSamples() {
        DischargeEstimator e=new DischargeEstimator();
        assertEquals(-1,e.update(0,80,4_000_000,false,20));
        assertEquals(-1,e.update(30000,80,3_990_000,false,20));
    }
    @Test public void chargeSlopeEstimatesTimeToSelectedThreshold() {
        DischargeEstimator e=new DischargeEstimator();
        e.update(0,80,4_000_000,false,20);
        assertEquals(3_540_000,e.update(60000,79,3_950_000,false,20));
    }
    @Test public void percentageFallbackWaitsForTwoTransitions() {
        DischargeEstimator e=new DischargeEstimator();
        e.update(0,80,Integer.MIN_VALUE,false,20);
        assertEquals(-1,e.update(60000,79,Integer.MIN_VALUE,false,20));
        assertEquals(3_480_000,e.update(120000,78,Integer.MIN_VALUE,false,20));
    }
    @Test public void pluggingInDiscardsOldRate() {
        DischargeEstimator e=new DischargeEstimator(); e.update(0,80,4_000_000,false,20);
        assertEquals(-1,e.update(60000,79,3_950_000,true,20));
        assertEquals(-1,e.update(120000,78,3_900_000,false,20));
    }
    @Test public void thresholdReachedAndRaisedAboveLevelStopImmediately() {
        DischargeEstimator e=new DischargeEstimator();
        assertEquals(0,e.update(0,20,1_000_000,false,20));
        assertEquals(0,e.update(10000,20,1_000_000,false,30));
    }
    @Test public void increasedLevelResetsRate() {
        DischargeEstimator e=new DischargeEstimator(); e.update(0,80,4_000_000,false,20);
        assertEquals(-1,e.update(60000,81,4_050_000,false,20));
    }
}