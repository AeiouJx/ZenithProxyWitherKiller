package com.aeioujx.witherkiller;

import java.util.ArrayList;
import java.util.List;

public class WitherKillerConfig {
    public final WitherConfig witherKiller = new WitherConfig();

    public static class WitherConfig {
        public static final List<String> DEFAULT_PROTECTED_BLOCKS = List.of(
            "REDSTONE_TORCH",
            "NOTE_BLOCK",
            "PISTON",
            "STICKY_PISTON",
            "PISTON_HEAD",
            "MOVING_PISTON",
            "OBSERVER",
            "DISPENSER",
            "DROPPER",
            "REDSTONE_WIRE",
            "REDSTONE_BLOCK",
            "REPEATER",
            "COMPARATOR"
        );

        public boolean enabled = false;
        public boolean targetConfigured = false;
        public double targetX = 0.0;
        public double targetY = 0.0;
        public double targetZ = 0.0;
        public int witherDetectionRange = 24;
        public int requiredWithersBeforeFight = 6;
        public int placementIntervalTicks = 1;
        public int witherSpawnWaitTicks = 20;
        public int fightStartTimeoutTicks = 200;
        public List<String> protectedBlocks = new ArrayList<>(DEFAULT_PROTECTED_BLOCKS);
    }
}
