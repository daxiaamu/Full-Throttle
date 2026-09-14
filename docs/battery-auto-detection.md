# Battery units and voltage compatibility

The standard API defines CURRENT_NOW in microamps and CHARGE_COUNTER in microamp-hours.
Reference: https://developer.android.com/reference/android/os/BatteryManager
Gauge resolution and correction can vary: https://source.android.com/docs/core/power/device

Version 1.0.20 uses one main-thread sampler for both themes and the foreground service.
The initial current scale follows the Android contract, except for locally verified device
profiles. No user choice or calibration factor is required; the obsolete currentUnit preference
is ignored. A profile can be overridden by runtime evidence after a firmware change.

Runtime verification integrates absolute raw current using a trapezoidal sum and compares it
with absolute charge-counter movement. Each window needs 30 seconds, 15 intervals and 2000 uAh
of movement. Windows without enough movement expire at 120 seconds. Two consecutive independent
windows must agree before a unit is confirmed. Samples less than 500 ms apart are ignored;
gaps over 5 seconds, plug changes, sign changes or unavailable samples discard pending evidence.
A contradictory or inconsistent window suppresses the power value until confirmation recovers.
Ratios of 0.25–4 indicate microamps; 250–4000 indicate milliamps. The broad acceptance bands
accommodate quantization and equivalent-capacity reporting while leaving a large rejection gap.
A 30 A plausibility ceiling prevents grossly mis-scaled current from being displayed.

Voltage uses the standard mV field when plausible (2000–20000 mV). When invalid, it tries the
battery_now_voltage_type vendor field with the same range validation. This was verified on
OPPO PLG110: the standard field is 3 while the vendor field reports approximately 3800 mV.
The truncated value is never multiplied into a fabricated precise voltage.

This verifies a scale, not absolute metrology or dual-cell topology. Missing counters cannot
confirm a scale; standard/profile interpretation remains indicated as such. Counter firmware
errors can still limit accuracy. Voltage and current are battery-side readings; while connected,
their product is net battery power rather than charger input or total device power.
No cell-count multiplier, nominal-voltage replacement, Root, privileged dumpsys or sysfs
access is used by the production sampler. No calibration result is persisted across process
restarts, so old firmware assumptions are rechecked.