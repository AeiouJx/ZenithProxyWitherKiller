package com.aeioujx.witherkiller.module;

import com.github.rfresh2.EventConsumer;
import com.zenith.Proxy;
import com.zenith.cache.data.entity.EntityLiving;
import com.zenith.cache.data.inventory.Container;
import com.zenith.event.client.ClientBotTick;
import com.zenith.feature.inventory.InventoryActionRequest;
import com.zenith.feature.inventory.actions.CloseContainer;
import com.zenith.feature.inventory.actions.DropMouseStack;
import com.zenith.feature.inventory.actions.InventoryAction;
import com.zenith.feature.inventory.actions.MoveToHotbarSlot;
import com.zenith.feature.inventory.actions.SetHeldItem;
import com.zenith.feature.player.ClickTarget;
import com.zenith.feature.player.Input;
import com.zenith.feature.player.InputRequest;
import com.zenith.feature.player.RotationHelper;
import com.zenith.feature.player.World;
import com.zenith.feature.player.raycast.RaycastHelper;
import com.zenith.mc.block.BlockRegistry;
import com.zenith.mc.item.ItemRegistry;
import com.zenith.module.impl.AbstractInventoryModule;
import com.zenith.module.impl.KillAura;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ClickItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.MoveToHotbarAction;
import com.zenith.util.timer.Timer;
import com.zenith.util.timer.Timers;
import org.cloudburstmc.math.vector.Vector2f;
import com.aeioujx.witherkiller.WitherKillerPlugin;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.zenith.Globals.BOT;
import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.CONFIG;
import static com.zenith.Globals.INPUTS;
import static com.zenith.Globals.INVENTORY;
import static com.zenith.Globals.MODULE;

public class WitherKillerModule extends AbstractInventoryModule {
    private static final int PRIORITY = 9500;
    private static final int SOUL_SAND_PER_WITHER = 4;
    private static final int CLEANUP_HOTBAR_SLOT = 1;
    private static final int[] CLEANUP_SIDE_ORDER = {0, -1, 1};
    private static final int[] CLEANUP_DEPTH_ORDER = {1, 2, 3};

    private final Timer placementTimer = Timers.tickTimer();
    private final Timer statusTimer = Timers.tickTimer();
    private Phase phase = Phase.PLACING;
    private int waitTicksRemaining = 0;
    private int expectedWithers = 0;
    private int noProgressTicksRemaining = 0;
    private int batchStartSoulSandCount = -1;
    private int placingStallTicks = 0;
    private CleanupTarget cleanupTarget;
    private KillAuraSnapshot killAuraSnapshot;

    public WitherKillerModule() {
        super(HandRestriction.MAIN_HAND, 0);
    }

    @Override
    public boolean enabledSetting() {
        return WitherKillerPlugin.PLUGIN_CONFIG.witherKiller.enabled;
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public boolean itemPredicate(ItemStack itemStack) {
        return itemStack.getId() == ItemRegistry.SOUL_SAND.id();
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(ClientBotTick.class, this::handleTick),
            of(ClientBotTick.Stopped.class, this::handleStopped)
        );
    }

    @Override
    public void onEnable() {
        if (!WitherKillerPlugin.PLUGIN_CONFIG.witherKiller.targetConfigured) {
            warn("WitherKiller requires a captured target before enabling");
            WitherKillerPlugin.PLUGIN_CONFIG.witherKiller.enabled = false;
            return;
        }
        resetCycle();
        snapshotKillAuraConfig();
        disableKillAura();
        info("WitherKiller enabled");
    }

    @Override
    public void onDisable() {
        resetCycle();
        disableKillAura();
        restoreKillAuraConfig();
        info("WitherKiller disabled");
    }

    public Phase getPhase() {
        return phase;
    }

    public int getSpawnWaitTicksRemaining() {
        return waitTicksRemaining;
    }

    public boolean captureTarget() {
        var raycast = RaycastHelper.playerBlockRaycast(5.0, false);
        if (!raycast.hit() || raycast.intersection() == null) {
            return false;
        }
        WitherKillerPlugin.PLUGIN_CONFIG.witherKiller.targetX = raycast.intersection().x();
        WitherKillerPlugin.PLUGIN_CONFIG.witherKiller.targetY = raycast.intersection().y();
        WitherKillerPlugin.PLUGIN_CONFIG.witherKiller.targetZ = raycast.intersection().z();
        WitherKillerPlugin.PLUGIN_CONFIG.witherKiller.targetConfigured = true;
        return true;
    }

    private void setPhase(Phase newPhase) {
        setPhase(newPhase, null);
    }

    private void setPhase(Phase newPhase, String reason) {
        if (phase == newPhase) {
            return;
        }
        phase = newPhase;
        if (reason == null || reason.isBlank()) {
            info("State -> {}", newPhase.name().toLowerCase());
        } else {
            info("State -> {} [{}]", newPhase.name().toLowerCase(), reason);
        }
    }

    private void handleTick(ClientBotTick event) {
        if (!Proxy.getInstance().isConnected() || Proxy.getInstance().hasActivePlayer()) {
            return;
        }
        if (!CACHE.getPlayerCache().isAlive()) {
            disableKillAura();
            return;
        }

        switch (phase) {
            case CLEANUP -> handleCleanup();
            case FIGHTING -> handleFighting();
            case WAITING_FOR_BATCH -> handleWaitingForBatch();
            case WAITING_FOR_FIGHT -> handleWaitingForFight();
            case PLACING -> handlePlacing();
        }
    }

    private void handleStopped(ClientBotTick.Stopped event) {
        resetCycle();
        disableKillAura();
    }

    private void handleCleanup() {
        disableKillAura();
        var target = currentCleanupTarget();
        if (target == null) {
            info("Cleanup complete, restarting summon cycle");
            resetCycle();
            return;
        }
        if (!ensureCleanupToolEquipped(target)) {
            INVENTORY.submit(InventoryActionRequest.noAction(this, getPriority() - 1));
            return;
        }
        cleanupTarget = target;
        info("Cleanup target -> ({}, {}, {}) block id {}", target.x(), target.y(), target.z(), World.getBlock(target.x(), target.y(), target.z()).id());
        var interactionCenter = World.blockInteractionCenter(target.x(), target.y(), target.z());
        var rotation = RotationHelper.rotationTo(
            interactionCenter.x(),
            interactionCenter.y(),
            interactionCenter.z()
        );
        INPUTS.submit(InputRequest.builder()
            .owner(this)
            .yaw(rotation.getX())
            .pitch(rotation.getY())
            .priority(getPriority())
            .input(Input.builder()
                .leftClick(true)
                .clickTarget(new ClickTarget.BlockPosition(target.x(), target.y(), target.z()))
                .build())
            .build());
    }

    private void handleWaitingForBatch() {
        disableKillAura();
        maintainFixedRotation();
        int witherCount = getNearbyWitherCount();
        if (hasActiveHostileWither()) {
            info("Detected hostile wither activation, starting kill phase");
            setPhase(Phase.FIGHTING, "hostile wither activated");
            enableKillAura();
            return;
        }
        if (waitTicksRemaining > 0) {
            waitTicksRemaining--;
            return;
        }
        expectedWithers = Math.max(expectedWithers, witherCount);
        noProgressTicksRemaining = WitherKillerPlugin.PLUGIN_CONFIG.witherKiller.fightStartTimeoutTicks;
        batchStartSoulSandCount = countSoulSandInInventory();
        if (hasCleanupBlockers()) {
            info("Summon batch timed out with blockers in front, starting cleanup");
            setPhase(Phase.CLEANUP, "summon timeout blocked");
            return;
        }
        setPhase(Phase.PLACING, "batch wait finished");
    }

    private void handleWaitingForFight() {
        disableKillAura();
        maintainFixedRotation();
        int witherCount = getNearbyWitherCount();
        if (hasActiveHostileWither()) {
            info("Detected hostile wither activation, starting kill phase");
            setPhase(Phase.FIGHTING, "hostile wither activated");
            enableKillAura();
            return;
        }
        if (witherCount >= WitherKillerPlugin.PLUGIN_CONFIG.witherKiller.requiredWithersBeforeFight) {
            setPhase(Phase.FIGHTING, "required wither count reached");
            enableKillAura();
            return;
        }
        if (waitTicksRemaining > 0) {
            waitTicksRemaining--;
            return;
        }
        if (witherCount > 0) {
            info("Wither count stalled at {}/{}, starting kill phase", witherCount, WitherKillerPlugin.PLUGIN_CONFIG.witherKiller.requiredWithersBeforeFight);
            setPhase(Phase.FIGHTING, "fight timeout reached");
            enableKillAura();
            return;
        }
        if (statusTimer.tick(100)) {
            warn("No wither remained after summon attempt, restarting cycle");
            resetCycle();
        }
    }

    private void handleFighting() {
        int witherCount = getNearbyWitherCount();
        if (witherCount > 0) {
            enableKillAura();
            return;
        }
        disableKillAura();
        resetCycle();
    }

    private void handlePlacing() {
        disableKillAura();
        maintainFixedRotation();
        if (hasActiveHostileWither()) {
            info("Detected hostile wither activation, starting kill phase");
            setPhase(Phase.FIGHTING, "hostile wither activated");
            enableKillAura();
            return;
        }
        int witherCount = getNearbyWitherCount();
        if (witherCount > expectedWithers) {
            expectedWithers = witherCount;
            noProgressTicksRemaining = WitherKillerPlugin.PLUGIN_CONFIG.witherKiller.fightStartTimeoutTicks;
            if (witherCount >= WitherKillerPlugin.PLUGIN_CONFIG.witherKiller.requiredWithersBeforeFight) {
                setPhase(Phase.FIGHTING, "required wither count reached");
                enableKillAura();
            } else {
                setPhase(Phase.WAITING_FOR_BATCH, "new wither detected");
                waitTicksRemaining = WitherKillerPlugin.PLUGIN_CONFIG.witherKiller.witherSpawnWaitTicks;
            }
            return;
        }
        if (witherCount >= WitherKillerPlugin.PLUGIN_CONFIG.witherKiller.requiredWithersBeforeFight) {
            setPhase(Phase.FIGHTING, "required wither count reached");
            enableKillAura();
            return;
        }
        if (witherCount > 0 && noProgressTicksRemaining <= 0) {
            info("Wither count stalled at {}/{}, starting kill phase", witherCount, WitherKillerPlugin.PLUGIN_CONFIG.witherKiller.requiredWithersBeforeFight);
            setPhase(Phase.FIGHTING, "fight timeout reached");
            enableKillAura();
            return;
        }
        if (witherCount > 0) {
            noProgressTicksRemaining--;
        } else {
            noProgressTicksRemaining = WitherKillerPlugin.PLUGIN_CONFIG.witherKiller.fightStartTimeoutTicks;
        }
        if (batchStartSoulSandCount < 0) {
            batchStartSoulSandCount = countSoulSandInInventory();
        }
        int soulSandConsumed = Math.max(0, batchStartSoulSandCount - countSoulSandInInventory());
        if (soulSandConsumed > 0) {
            placingStallTicks = 0;
        } else if (hasCleanupBlockers()) {
            placingStallTicks++;
            if (placingStallTicks >= getPlacementStallTimeoutTicks()) {
                info("Placement stalled with blockers in front, starting cleanup");
                setPhase(Phase.CLEANUP, "placement stalled");
                return;
            }
        } else {
            placingStallTicks = 0;
        }
        if (soulSandConsumed >= SOUL_SAND_PER_WITHER) {
            placingStallTicks = 0;
            setPhase(Phase.WAITING_FOR_BATCH, "4 soul sand consumed");
            waitTicksRemaining = WitherKillerPlugin.PLUGIN_CONFIG.witherKiller.witherSpawnWaitTicks;
            return;
        }
        var inventoryActionResult = doInventoryActionsV2();
        if (inventoryActionResult.state() == ActionState.SWAPPING) {
            INVENTORY.submit(InventoryActionRequest.noAction(this, getPriority() - 1));
            return;
        }
        if (inventoryActionResult.state() == ActionState.NO_ITEM) {
            if (statusTimer.tick(100)) {
                warn("No soul sand found in the inventory");
            }
            return;
        }
        if (!placementTimer.tick(WitherKillerPlugin.PLUGIN_CONFIG.witherKiller.placementIntervalTicks)) {
            return;
        }
        submitPlacementClick();
    }

    private void submitPlacementClick() {
        var rotation = getPlacementRotation();
        INPUTS.submit(InputRequest.builder()
            .owner(this)
            .yaw(rotation.getX())
            .pitch(rotation.getY())
            .priority(getPriority())
            .input(Input.builder()
                .rightClick(true)
                .hand(Hand.MAIN_HAND)
                .clickTarget(ClickTarget.AnyBlock.INSTANCE)
                .build())
            .build());
    }

    private void maintainFixedRotation() {
        var rotation = getPlacementRotation();
        INPUTS.submit(InputRequest.builder()
            .owner(this)
            .yaw(rotation.getX())
            .pitch(rotation.getY())
            .priority(getPriority() - 1)
            .build());
    }

    private Vector2f getPlacementRotation() {
        var config = WitherKillerPlugin.PLUGIN_CONFIG.witherKiller;
        return RotationHelper.rotationTo(config.targetX, config.targetY, config.targetZ);
    }

    private int getNearbyWitherCount() {
        double maxDistanceSq = Math.pow(WitherKillerPlugin.PLUGIN_CONFIG.witherKiller.witherDetectionRange, 2);
        int count = 0;
        for (var entity : CACHE.getEntityCache().getEntities().values()) {
            if (!(entity instanceof EntityLiving living)) continue;
            if (living.getEntityType() != EntityType.WITHER) continue;
            if (!living.isAlive()) continue;
            if (CACHE.getPlayerCache().distanceSqToSelf(living) <= maxDistanceSq) {
                count++;
            }
        }
        return count;
    }

    private boolean hasActiveHostileWither() {
        double maxDistanceSq = Math.pow(WitherKillerPlugin.PLUGIN_CONFIG.witherKiller.witherDetectionRange, 2);
        for (var entity : CACHE.getEntityCache().getEntities().values()) {
            if (!(entity instanceof EntityLiving living)) continue;
            if (living.getEntityType() != EntityType.WITHER) continue;
            if (!living.isAlive()) continue;
            if (CACHE.getPlayerCache().distanceSqToSelf(living) > maxDistanceSq) continue;
            Integer invulnerableTicks = living.getMetadataValue(19, MetadataTypes.INT, Integer.class);
            if (invulnerableTicks == null || invulnerableTicks <= 0) {
                return true;
            }
        }
        return false;
    }

    private int countSoulSandInInventory() {
        int count = 0;
        for (var itemStack : CACHE.getPlayerCache().getPlayerInventory()) {
            if (itemStack == null) continue;
            if (itemStack.getId() != ItemRegistry.SOUL_SAND.id()) continue;
            count += itemStack.getAmount();
        }
        return count;
    }

    private boolean hasCleanupBlockers() {
        return nextCleanupTarget() != null;
    }

    private CleanupTarget currentCleanupTarget() {
        if (cleanupTarget != null
            && isCleanupBlock(World.getBlock(cleanupTarget.x(), cleanupTarget.y(), cleanupTarget.z()))) {
            return cleanupTarget;
        }
        cleanupTarget = nextCleanupTarget();
        return cleanupTarget;
    }

    private CleanupTarget nextCleanupTarget() {
        var config = WitherKillerPlugin.PLUGIN_CONFIG.witherKiller;
        double dx = config.targetX - CACHE.getPlayerCache().getX();
        double dz = config.targetZ - CACHE.getPlayerCache().getZ();
        int forwardX = Math.abs(dx) >= Math.abs(dz) ? (dx >= 0 ? 1 : -1) : 0;
        int forwardZ = Math.abs(dz) > Math.abs(dx) ? (dz >= 0 ? 1 : -1) : 0;
        if (forwardX == 0 && forwardZ == 0) {
            return null;
        }
        int rightX = -forwardZ;
        int rightZ = forwardX;
        int botX = (int) Math.floor(CACHE.getPlayerCache().getX());
        int botY = (int) Math.floor(config.targetY);
        int botZ = (int) Math.floor(CACHE.getPlayerCache().getZ());

        for (int depth : CLEANUP_DEPTH_ORDER) {
            for (int side : CLEANUP_SIDE_ORDER) {
                int x = botX + (forwardX * depth) + (rightX * side);
                int y = botY;
                int z = botZ + (forwardZ * depth) + (rightZ * side);
                if (isCleanupBlock(World.getBlock(x, y, z))) {
                    return new CleanupTarget(x, y, z);
                }
            }
        }
        return null;
    }

    private boolean isCleanupBlock(com.zenith.mc.block.Block block) {
        return block != null && !block.isAir() && !isProtectedMachineBlock(block);
    }

    private boolean isProtectedMachineBlock(com.zenith.mc.block.Block block) {
        if (block == BlockRegistry.OBSIDIAN || block == BlockRegistry.CRYING_OBSIDIAN) {
            return true;
        }
        return getConfiguredProtectedBlocks().contains(block);
    }

    private Set<com.zenith.mc.block.Block> getConfiguredProtectedBlocks() {
        var configuredNames = WitherKillerPlugin.PLUGIN_CONFIG.witherKiller.protectedBlocks;
        Set<com.zenith.mc.block.Block> blocks = new HashSet<>();
        if (configuredNames == null) {
            return blocks;
        }
        for (var name : configuredNames) {
            var block = resolveBlockByName(name);
            if (block != null) {
                blocks.add(block);
            }
        }
        return blocks;
    }

    public static com.zenith.mc.block.Block resolveBlockByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String normalized = name.trim().toUpperCase(Locale.ROOT);
        try {
            Field field = BlockRegistry.class.getField(normalized);
            Object value = field.get(null);
            if (value instanceof com.zenith.mc.block.Block block) {
                return block;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    private boolean ensureCleanupToolEquipped(CleanupTarget target) {
        var targetBlock = World.getBlock(target.x(), target.y(), target.z());
        int bestSlot = bestCleanupToolSlot(targetBlock);
        int heldSlot = CACHE.getPlayerCache().getHeldItemSlot() + 36;
        if (heldSlot == bestSlot || bestSlot == -1) {
            return true;
        }
        if (INVENTORY.hasActiveRequest()) {
            return false;
        }
        List<InventoryAction> actions = new ArrayList<>();
        int openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainerId();
        if (openContainer != 0) {
            actions.add(new CloseContainer(openContainer));
        }
        if (CACHE.getPlayerCache().getInventoryCache().getMouseStack() != Container.EMPTY_STACK) {
            actions.add(new DropMouseStack(ClickItemAction.LEFT_CLICK));
        }
        if (bestSlot >= 36 && bestSlot <= 44) {
            actions.add(new SetHeldItem(bestSlot - 36));
        } else {
            actions.add(new MoveToHotbarSlot(bestSlot, MoveToHotbarAction.from(CLEANUP_HOTBAR_SLOT)));
            actions.add(new SetHeldItem(CLEANUP_HOTBAR_SLOT));
        }
        info("Switching to cleanup tool from slot {} for block id {}", bestSlot, targetBlock.id());
        INVENTORY.submit(InventoryActionRequest.builder()
            .owner(this)
            .actions(actions)
            .priority(getPriority())
            .build());
        return false;
    }

    private int bestCleanupToolSlot(com.zenith.mc.block.Block block) {
        var inventory = CACHE.getPlayerCache().getPlayerInventory();
        int bestPreferredSlot = CACHE.getPlayerCache().getHeldItemSlot() + 36;
        double bestPreferredSpeed = BOT.getInteractions().blockBreakSpeed(
            block,
            CACHE.getPlayerCache().getEquipment(org.geysermc.mcprotocollib.protocol.data.game.entity.EquipmentSlot.MAIN_HAND)
        );
        for (int i = 9; i <= 44; i++) {
            ItemStack itemStack = inventory.get(i);
            if (itemStack == null || itemStack == Container.EMPTY_STACK) continue;
            double speed = BOT.getInteractions().blockBreakSpeed(block, itemStack);
            if (speed > bestPreferredSpeed) {
                bestPreferredSpeed = speed;
                bestPreferredSlot = i;
            }
        }
        return bestPreferredSlot;
    }

    private int getPlacementStallTimeoutTicks() {
        return Math.max(WitherKillerPlugin.PLUGIN_CONFIG.witherKiller.witherSpawnWaitTicks, 20);
    }

    private void resetCycle() {
        setPhase(Phase.PLACING, "cycle reset");
        waitTicksRemaining = 0;
        expectedWithers = 0;
        noProgressTicksRemaining = WitherKillerPlugin.PLUGIN_CONFIG.witherKiller.fightStartTimeoutTicks;
        batchStartSoulSandCount = countSoulSandInInventory();
        placingStallTicks = 0;
        cleanupTarget = null;
        placementTimer.reset();
        statusTimer.reset();
    }

    private void snapshotKillAuraConfig() {
        if (killAuraSnapshot != null) {
            return;
        }
        killAuraSnapshot = new KillAuraSnapshot(
            CONFIG.client.extra.killAura.enabled,
            CONFIG.client.extra.killAura.targetPlayers,
            CONFIG.client.extra.killAura.targetHostileMobs,
            CONFIG.client.extra.killAura.onlyHostileAggressive,
            CONFIG.client.extra.killAura.targetNeutralMobs,
            CONFIG.client.extra.killAura.onlyNeutralAggressive,
            CONFIG.client.extra.killAura.targetCustom,
            new ArrayList<>(CONFIG.client.extra.killAura.customTargets)
        );
    }

    private void restoreKillAuraConfig() {
        if (killAuraSnapshot == null) {
            return;
        }
        CONFIG.client.extra.killAura.enabled = killAuraSnapshot.enabled();
        CONFIG.client.extra.killAura.targetPlayers = killAuraSnapshot.targetPlayers();
        CONFIG.client.extra.killAura.targetHostileMobs = killAuraSnapshot.targetHostileMobs();
        CONFIG.client.extra.killAura.onlyHostileAggressive = killAuraSnapshot.onlyHostileAggressive();
        CONFIG.client.extra.killAura.targetNeutralMobs = killAuraSnapshot.targetNeutralMobs();
        CONFIG.client.extra.killAura.onlyNeutralAggressive = killAuraSnapshot.onlyNeutralAggressive();
        CONFIG.client.extra.killAura.targetCustom = killAuraSnapshot.targetCustom();
        CONFIG.client.extra.killAura.customTargets.clear();
        CONFIG.client.extra.killAura.customTargets.addAll(killAuraSnapshot.customTargets());
        MODULE.get(KillAura.class).syncEnabledFromConfig();
        killAuraSnapshot = null;
    }

    private void enableKillAura() {
        boolean needsSync = !CONFIG.client.extra.killAura.enabled
            || CONFIG.client.extra.killAura.targetPlayers
            || CONFIG.client.extra.killAura.targetHostileMobs
            || CONFIG.client.extra.killAura.onlyHostileAggressive
            || CONFIG.client.extra.killAura.targetNeutralMobs
            || CONFIG.client.extra.killAura.onlyNeutralAggressive
            || !CONFIG.client.extra.killAura.targetCustom
            || CONFIG.client.extra.killAura.customTargets.size() != 1
            || !CONFIG.client.extra.killAura.customTargets.contains(EntityType.WITHER);
        CONFIG.client.extra.killAura.targetPlayers = false;
        CONFIG.client.extra.killAura.targetHostileMobs = false;
        CONFIG.client.extra.killAura.onlyHostileAggressive = false;
        CONFIG.client.extra.killAura.targetNeutralMobs = false;
        CONFIG.client.extra.killAura.onlyNeutralAggressive = false;
        CONFIG.client.extra.killAura.targetCustom = true;
        CONFIG.client.extra.killAura.customTargets.clear();
        CONFIG.client.extra.killAura.customTargets.add(EntityType.WITHER);
        CONFIG.client.extra.killAura.enabled = true;
        if (needsSync) {
            MODULE.get(KillAura.class).syncEnabledFromConfig();
        }
    }

    private void disableKillAura() {
        if (CONFIG.client.extra.killAura.enabled) {
            CONFIG.client.extra.killAura.enabled = false;
            MODULE.get(KillAura.class).syncEnabledFromConfig();
        }
    }

    public enum Phase {
        PLACING,
        CLEANUP,
        WAITING_FOR_BATCH,
        WAITING_FOR_FIGHT,
        FIGHTING
    }

    private record CleanupTarget(int x, int y, int z) {
    }

    private record KillAuraSnapshot(
        boolean enabled,
        boolean targetPlayers,
        boolean targetHostileMobs,
        boolean onlyHostileAggressive,
        boolean targetNeutralMobs,
        boolean onlyNeutralAggressive,
        boolean targetCustom,
        List<EntityType> customTargets
    ) {
    }
}
