package com.imc.restaurant;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

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
    public void start(LocalPlayer player) {
        if (running) {
            IMCRestaurantMod.send(player,Component.literal("§e[IMC] 自动流程已在运行中。"));
            return;
        }
        if (bindingManager.boundCount() < DishList.DISH_COUNT) {
            IMCRestaurantMod.send(player,Component.literal("§c[IMC] 请先按 B 完成木桶绑定（需要 16 个）。"));
            return;
        }
        if (!orderManager.readOrder(player)) {
            IMCRestaurantMod.send(player,Component.literal("§c[IMC] 订单读取失败，自动流程未启动。"));
            return;
        }
        pendingDishes.clear();
        pendingDishes.addAll(orderManager.getCurrentOrder());
        if (pendingDishes.isEmpty()) {
            IMCRestaurantMod.send(player,Component.literal("§c[IMC] 订单为空。"));
            return;
        }
        running = true;
        IMCRestaurantMod.send(player,Component.literal("§a[IMC] 自动流程启动，共 §f" + pendingDishes.size()
                + " §a道菜。按 §fJ §a随时终止。"));
        nextDish(player);
    }

    /** 终止整个流程（玩家按 J）。 */
    public void stop(LocalPlayer player) {
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
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            mc.setScreen(null);
        }
        IMCRestaurantMod.send(player,Component.literal("§c[IMC] 自动流程已终止。"));
    }

    /** 由客户端 tick 每tick调用一次。 */
    public void tick(LocalPlayer player) {
        if (!running) return;
        if (player == null) return;
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        switch (state) {
            case TURN_TO_BARREL -> {
                BlockPos pos = bindingManager.getBindings().get(currentDish);
                if (pos == null) {
                    IMCRestaurantMod.send(player,Component.literal("§c[IMC] 菜 §e" + currentDish
                            + " §c没有对应的木桶，跳过。"));
                    onDishFinished(player);
                    return;
                }
                lookAt(player, barrelCenter(pos));
                waitTicks = 2; // 等待转头稳定
                state = State.OPEN_BARREL;
            }
            case OPEN_BARREL -> openTargetedBarrel(player);
            case TAKE_ITEMS -> takeItemsFromBarrel(player);
            case CLOSE_BARREL -> {
                if (mc.screen != null) {
                    mc.setScreen(null);
                }
                waitTicks = 2;
                state = State.AIM_MONSTER;
            }
            case AIM_MONSTER -> aimNearestMonster(player);
            case FEED -> feedStep(player);
            case DONE -> onDishFinished(player);
            default -> {
            }
        }
    }

    private void nextDish(LocalPlayer player) {
        if (pendingDishes.isEmpty()) {
            IMCRestaurantMod.send(player,Component.literal("§b[IMC] 所有菜已交付完毕，按 §fJ §b重新读取订单继续。"));
            running = false;
            state = State.IDLE;
            return;
        }
        currentDish = pendingDishes.remove(0);
        IMCRestaurantMod.send(player,Component.literal("§d[IMC] 开始处理：§e" + currentDish));
        state = State.TURN_TO_BARREL;
        waitTicks = 0;
    }

    private void onDishFinished(LocalPlayer player) {
        currentDish = null;
        targetMonster = null;
        feedSlot = 0;
        nextDish(player);
    }

    // ---------------- 动作实现 ----------------

    private void openTargetedBarrel(LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) {
            IMCRestaurantMod.send(player,Component.literal("§c[IMC] 没对准方块，跳过该菜。"));
            onDishFinished(player);
            return;
        }
        BlockPos pos = ((BlockHitResult) mc.hitResult).getBlockPos();
        Direction side = ((BlockHitResult) mc.hitResult).getDirection();
        Vec3 hitVec = mc.hitResult.getLocation();
        if (mc.gameMode != null) {
            mc.gameMode.useItemOn(
                    player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(hitVec, side, pos, false));
            player.swing(InteractionHand.MAIN_HAND);
        }
        // 等待服务端打开 GUI
        waitTicks = 4;
        state = State.TAKE_ITEMS;
    }

    /**
     * 从木桶容器里取出「单个」物品到玩家背包（非整组）。
     * 操作的是当前打开的 ScreenHandler。
     *
     * 实现：左键点击容器槽拿起整组到光标 → 右键点击玩家空背包槽放一个 →
     *      左键点击容器槽把光标剩余放回。
     */
    private void takeItemsFromBarrel(LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.containerMenu == null
                || mc.screen == null) {
            IMCRestaurantMod.send(player,Component.literal("§c[IMC] 木桶未打开，跳过取物。"));
            onDishFinished(player);
            return;
        }
        var handler = mc.player.containerMenu;
        int totalSlots = handler.slots.size();
        // 玩家背包在 screen handler 末尾：通常 27 主背包 + 9 热栏
        int hotbarStart = totalSlots - 9;

        // 木桶物品槽范围：0 .. hotbarStart - 27 - 1
        int containerSlots = hotbarStart - 27;

        // 找容器里第一个有物品的槽
        int sourceSlot = -1;
        for (int i = 0; i < containerSlots; i++) {
            if (!handler.getSlot(i).getItem().isEmpty()) {
                sourceSlot = i;
                break;
            }
        }
        if (sourceSlot < 0) {
            // 发送到公共聊天栏，让前台 bot "说"出来（服务器所有人可见）
            if (mc.player.connection != null) {
                mc.player.connection.sendChat(currentDish + " 菜品没了！");
            }
            IMCRestaurantMod.send(player,Component.literal("§c[IMC] §e" + currentDish + " §c菜品没了！"));
            onDishFinished(player);
            return;
        }

        // 找玩家背包里第一个空槽（优先热栏 1~5，即 totalSlots-9 .. totalSlots-5）
        int destSlot = -1;
        for (int i = hotbarStart; i < totalSlots; i++) {
            if (handler.getSlot(i).getItem().isEmpty()) {
                destSlot = i;
                break;
            }
        }
        if (destSlot < 0) {
            IMCRestaurantMod.send(player,Component.literal("§c[IMC] 背包已满，跳过取物。"));
            onDishFinished(player);
            return;
        }

        if (mc.gameMode != null) {
            // 1) 左键容器槽：拿起整组到光标
            mc.gameMode.handleInventoryMouseClick(
                    handler.containerId, sourceSlot, 0, ClickType.PICKUP, mc.player);
            // 2) 右键玩家空槽：放下一个物品到背包
            mc.gameMode.handleInventoryMouseClick(
                    handler.containerId, destSlot, 1, ClickType.PICKUP, mc.player);
            // 3) 左键容器槽：把光标剩余物品放回木桶
            mc.gameMode.handleInventoryMouseClick(
                    handler.containerId, sourceSlot, 0, ClickType.PICKUP, mc.player);
        }
        IMCRestaurantMod.send(player,Component.literal(
                String.format("§7[IMC] 从 §e%s §7木桶取出 §f1 §7个物品到背包。", currentDish)));
        waitTicks = 2;
        state = State.CLOSE_BARREL;
    }

    private void aimNearestMonster(LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            onDishFinished(player);
            return;
        }
        Vec3 eye = player.getEyePosition();
        double radius = 32.0;
        AABB box = new AABB(
                eye.x - radius, eye.y - radius, eye.z - radius,
                eye.x + radius, eye.y + radius, eye.z + radius);
        Entity nearest = null;
        double nearestDist = Double.MAX_VALUE;
        // 扫描所有生物（除玩家自己、盔甲架外）
        for (Entity e : mc.level.getEntities(player, box)) {
            if (e instanceof net.minecraft.world.entity.decoration.ArmorStand) continue;
            if (e instanceof Player) continue;
            double d = e.distanceToSqr(eye);
            if (d < nearestDist) {
                nearestDist = d;
                nearest = e;
            }
        }
        if (nearest == null) {
            IMCRestaurantMod.send(player,Component.literal("§c[IMC] 附近没有生物，跳过喂食。"));
            onDishFinished(player);
            return;
        }
        targetMonster = nearest;
        lookAt(player, nearest.getEyePosition());
        IMCRestaurantMod.send(player,Component.literal(
                "§d[IMC] 已瞄准生物：§f" + nearest.getName().getString()));
        feedSlot = 0;
        waitTicks = 3;
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
    private void feedStep(LocalPlayer player) {
        if (targetMonster == null || !targetMonster.isAlive()) {
            IMCRestaurantMod.send(player,Component.literal("§c[IMC] 目标怪物已消失。"));
            onDishFinished(player);
            return;
        }
        if (feedSlot >= 5) {
            // 1~5 槽全部使用完毕，本道菜完成
            IMCRestaurantMod.send(player,Component.literal("§a[IMC] §e" + currentDish + " §a喂食完毕。"));
            onDishFinished(player);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        // 1) 切换到目标热栏槽（0~4 对应快捷栏1~5）
        mc.player.getInventory().setSelectedSlot(feedSlot);
        // 2) 先右键点一下（按 trae.md：切换之前记得先再右键点一下）
        useItemOnMonster(player);
        waitTicks = 2;
        // 3) 切到下一槽，下一 tick 再右键
        feedSlot++;
        if (feedSlot < 5) {
            mc.player.getInventory().setSelectedSlot(feedSlot);
            useItemOnMonster(player);
            waitTicks += 2;
            feedSlot++;
        }
    }

    private void useItemOnMonster(LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        // 优先对准目标实体右键；否则对准准星方向使用物品
        if (targetMonster != null) {
            lookAt(player, targetMonster.getEyePosition());
            if (mc.gameMode != null) {
                mc.gameMode.interact(
                        player, targetMonster, InteractionHand.MAIN_HAND);
            }
        }
        // 同时触发 useItem，保证右键效果（喂食/投掷）
        if (mc.gameMode != null) {
            mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        }
        player.swing(InteractionHand.MAIN_HAND);
    }

    // ---------------- 工具 ----------------

    private Vec3 barrelCenter(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    /** 让玩家看向指定坐标点。 */
    private void lookAt(LocalPlayer player, Vec3 target) {
        Vec3 eye = player.getEyePosition();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double distXZ = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, distXZ));
        player.setYRot(yaw);
        player.setXRot(pitch);
        // 立即同步，避免插值平滑
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.yHeadRot = yaw;
        player.yBodyRot = yaw;
    }
}
