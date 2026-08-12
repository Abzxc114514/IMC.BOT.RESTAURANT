package com.imc.restaurant;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * 自动化执行控制器（trae.md 第3~7节）。
 *
 * 状态机：
 *   IDLE
 *   -> TURN_TO_BARREL        (转头对准木桶)
 *   -> OPEN_BARREL            (右键打开木桶)
 *   -> TAKE_ITEMS             (从木桶GUI取物品到热栏1~5)
 *   -> CLOSE_BARREL           (关闭木桶GUI)
 *   -> AIM_MONSTER            (对准最近的生物，除玩家与盔甲架)
 *   -> FEED                   (切换热栏物品并右键喂食)
 *   -> DONE                   (本道菜完成，继续下一道菜，或回到读取订单)
 *
 * 第7步：按 J 终止整个流程，回到 IDLE。
 */
public class AutomationController {

    private enum State {
        IDLE,
        TURN_TO_BARREL,
        OPEN_BARREL,
        TAKE_ITEMS,
        CLOSE_BARREL,
        AIM_MONSTER,
        FEED,
        DONE
    }

    /** 每个状态的等待计数（tick），用于让动作有完成时间。 */
    private int waitTicks = 0;

    private State state = State.IDLE;
    private boolean running = false;

    /** 本次订单中待处理的菜名队列。 */
    private final List<String> pendingDishes = new ArrayList<>();
    /** 当前正在处理的菜名。 */
    private String currentDish;

    /** 当前喂食时切换的热栏槽位索引。 */
    private int feedSlot = 0;

    private final BarrelBindingManager bindingManager;
    private final OrderManager orderManager;

    /** 最近一次瞄准的怪物实体，用于喂食。 */
    private Entity targetMonster;

    public AutomationController(BarrelBindingManager bm, OrderManager om) {
        this.bindingManager = bm;
        this.orderManager = om;
    }

    public boolean isRunning() {
        return running;
    }

    /** 启动整个自动流程：读取订单 -> 依次取餐喂食。 */
    public void start(ClientPlayerEntity player) {
        if (running) {
            player.sendMessage(Text.literal("§e[IMC] 自动流程已在运行中。"), false);
            return;
        }
        if (bindingManager.boundCount() < DishList.DISH_COUNT) {
            player.sendMessage(Text.literal("§c[IMC] 请先按 B 完成木桶绑定（需要 16 个）。"), false);
            return;
        }
        if (!orderManager.readOrder(player)) {
            player.sendMessage(Text.literal("§c[IMC] 订单读取失败，自动流程未启动。"), false);
            return;
        }
        pendingDishes.clear();
        pendingDishes.addAll(orderManager.getCurrentOrder());
        if (pendingDishes.isEmpty()) {
            player.sendMessage(Text.literal("§c[IMC] 订单为空。"), false);
            return;
        }
        running = true;
        player.sendMessage(Text.literal("§a[IMC] 自动流程启动，共 §f" + pendingDishes.size()
                + " §a道菜。按 §fJ §a随时终止。"), false);
        nextDish(player);
    }

    /** 终止整个流程（玩家按 J）。 */
    public void stop(ClientPlayerEntity player) {
        if (!running) {
            // 没在运行时按 J，则尝试启动一次（按 trae.md 第7步重新走流程）
            start(player);
            return;
        }
        running = false;
        state = State.IDLE;
        pendingDishes.clear();
        currentDish = null;
        targetMonster = null;
        // 关闭可能打开的GUI
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null) {
            mc.currentScreen.close();
            mc.setScreen(null);
        }
        player.sendMessage(Text.literal("§c[IMC] 自动流程已终止。"), false);
    }

    /** 由客户端 tick 每tick调用一次。 */
    public void tick(ClientPlayerEntity player) {
        if (!running) return;
        if (player == null) return;
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        switch (state) {
            case TURN_TO_BARREL -> {
                BlockPos pos = bindingManager.getBindings().get(currentDish);
                if (pos == null) {
                    player.sendMessage(Text.literal("§c[IMC] 菜 §e" + currentDish
                            + " §c没有对应的木桶，跳过。"), false);
                    onDishFinished(player);
                    return;
                }
                lookAt(player, barrelCenter(pos));
                waitTicks = 5; // 等待转头稳定
                state = State.OPEN_BARREL;
            }
            case OPEN_BARREL -> openTargetedBarrel(player);
            case TAKE_ITEMS -> takeItemsFromBarrel(player);
            case CLOSE_BARREL -> {
                if (mc.currentScreen != null) {
                    mc.currentScreen.close();
                    mc.setScreen(null);
                }
                waitTicks = 5;
                state = State.AIM_MONSTER;
            }
            case AIM_MONSTER -> aimNearestMonster(player);
            case FEED -> feedStep(player);
            case DONE -> onDishFinished(player);
            default -> {
            }
        }
    }

    private void nextDish(ClientPlayerEntity player) {
        if (pendingDishes.isEmpty()) {
            player.sendMessage(Text.literal("§b[IMC] 所有菜已交付完毕，按 §fJ §b重新读取订单继续。"), false);
            running = false;
            state = State.IDLE;
            return;
        }
        currentDish = pendingDishes.remove(0);
        player.sendMessage(Text.literal("§d[IMC] 开始处理：§e" + currentDish), false);
        state = State.TURN_TO_BARREL;
        waitTicks = 0;
    }

    private void onDishFinished(ClientPlayerEntity player) {
        currentDish = null;
        targetMonster = null;
        feedSlot = 0;
        nextDish(player);
    }

    // ---------------- 动作实现 ----------------

    private void openTargetedBarrel(ClientPlayerEntity player) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.BLOCK) {
            player.sendMessage(Text.literal("§c[IMC] 没对准方块，跳过该菜。"), false);
            onDishFinished(player);
            return;
        }
        BlockPos pos = ((BlockHitResult) mc.crosshairTarget).getBlockPos();
        Direction side = ((BlockHitResult) mc.crosshairTarget).getSide();
        Vec3d hitVec = mc.crosshairTarget.getPos();
        if (mc.interactionManager != null) {
            mc.interactionManager.interactBlock(
                    player, Hand.MAIN_HAND,
                    new BlockHitResult(hitVec, side, pos, false));
            player.swingHand(Hand.MAIN_HAND);
        }
        // 等待服务端打开 GUI
        waitTicks = 10;
        state = State.TAKE_ITEMS;
    }

    /**
     * 把木桶容器里的物品转移到玩家热栏 1~5 槽（slot index 0~4）。
     * 操作的是当前打开的 ScreenHandler。
     */
    private void takeItemsFromBarrel(ClientPlayerEntity player) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.player.currentScreenHandler == null
                || mc.currentScreen == null) {
            player.sendMessage(Text.literal("§c[IMC] 木桶未打开，跳过取物。"), false);
            onDishFinished(player);
            return;
        }
        var handler = mc.player.currentScreenHandler;
        int totalSlots = handler.slots.size();
        // 玩家背包在 screen handler 末尾：通常 27 主背包 + 9 热栏
        // 热栏 slot index = totalSlots - 9 .. totalSlots - 1
        int hotbarStart = totalSlots - 9;

        // 木桶物品槽范围：从 0 到 hotbarStart - 27 - 1 之间是容器槽
        // 简单做法：遍历所有槽，找到属于容器（非玩家背包）的槽，把物品 shift 移到玩家背包
        int containerSlots = hotbarStart - 27; // 容器槽位数（如木桶27）
        int moved = 0;
        for (int i = 0; i < containerSlots && moved < 5; i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack.isEmpty()) continue;
            // shift+左键点击容器槽 -> 自动合并到玩家背包
            if (mc.interactionManager != null) {
                mc.interactionManager.clickSlot(
                        handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
            }
            moved++;
        }
        player.sendMessage(Text.literal(
                String.format("§7[IMC] 从 §e%s §7木桶取出 §f%d §7组物品到背包。", currentDish, moved)), false);
        waitTicks = 4;
        state = State.CLOSE_BARREL;
    }

    private void aimNearestMonster(ClientPlayerEntity player) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) {
            onDishFinished(player);
            return;
        }
        Vec3d eye = player.getEyePos();
        double radius = 32.0;
        Box box = new Box(
                eye.x - radius, eye.y - radius, eye.z - radius,
                eye.x + radius, eye.y + radius, eye.z + radius);
        Entity nearest = null;
        double nearestDist = Double.MAX_VALUE;
        // 扫描所有生物（除玩家自己、盔甲架外）
        for (Entity e : mc.world.getOtherEntities(player, box)) {
            if (e instanceof net.minecraft.entity.decoration.ArmorStandEntity) continue;
            if (e instanceof net.minecraft.entity.player.PlayerEntity) continue;
            double d = e.squaredDistanceTo(eye);
            if (d < nearestDist) {
                nearestDist = d;
                nearest = e;
            }
        }
        if (nearest == null) {
            player.sendMessage(Text.literal("§c[IMC] 附近没有生物，跳过喂食。"), false);
            onDishFinished(player);
            return;
        }
        targetMonster = nearest;
        lookAt(player, nearest.getEyePos());
        player.sendMessage(Text.literal(
                "§d[IMC] 已瞄准生物：§f" + nearest.getName().getString()), false);
        feedSlot = 0;
        waitTicks = 8;
        state = State.FEED;
    }

    /**
     * 喂食步骤（trae.md 第6节）：
     *   将菜名里的物品挨个切换，切换一次右键点击一下。
     *   注意：切换之前记得先再右键点一下。
     *
     * 实现：先把当前手持物品右键一次（预喂），再切到下一个热栏槽，
     *      再右键一次。直到热栏1~5都使用完，则本道菜完成。
     */
    private void feedStep(ClientPlayerEntity player) {
        if (targetMonster == null || !targetMonster.isAlive()) {
            player.sendMessage(Text.literal("§c[IMC] 目标怪物已消失。"), false);
            onDishFinished(player);
            return;
        }
        if (feedSlot >= 5) {
            // 1~5 槽全部使用完毕，本道菜完成
            player.sendMessage(Text.literal("§a[IMC] §e" + currentDish + " §a喂食完毕。"), false);
            onDishFinished(player);
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        // 1) 切换到目标热栏槽（0~4 对应快捷栏1~5）
        mc.player.getInventory().selectedSlot = feedSlot;
        // 2) 先右键点一下（按 trae.md：切换之前记得先再右键点一下）
        useItemOnMonster(player);
        waitTicks = 6;
        // 3) 切到下一槽，下一 tick 再右键
        feedSlot++;
        if (feedSlot < 5) {
            mc.player.getInventory().selectedSlot = feedSlot;
            useItemOnMonster(player);
            waitTicks += 6;
            feedSlot++;
        }
    }

    private void useItemOnMonster(ClientPlayerEntity player) {
        MinecraftClient mc = MinecraftClient.getInstance();
        // 优先对准目标实体右键；否则对准准星方向使用物品
        if (targetMonster != null) {
            lookAt(player, targetMonster.getEyePos());
            if (mc.interactionManager != null) {
                mc.interactionManager.interactEntityAtLocation(
                        player, targetMonster,
                        new EntityHitResult(targetMonster,
                                targetMonster.getEyePos()),
                        Hand.MAIN_HAND);
            }
        }
        // 同时触发 useItem，保证右键效果（喂食/投掷）
        if (mc.interactionManager != null) {
            mc.interactionManager.interactItem(player, Hand.MAIN_HAND);
        }
        player.swingHand(Hand.MAIN_HAND);
    }

    // ---------------- 工具 ----------------

    private Vec3d barrelCenter(BlockPos pos) {
        return new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    /** 让玩家看向指定坐标点。 */
    private void lookAt(ClientPlayerEntity player, Vec3d target) {
        Vec3d eye = player.getEyePos();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double distXZ = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, distXZ));
        player.setYaw(yaw);
        player.setPitch(pitch);
        // 立即同步，避免插值平滑
        player.prevYaw = yaw;
        player.prevPitch = pitch;
        player.headYaw = yaw;
        player.bodyYaw = yaw;
    }
}
