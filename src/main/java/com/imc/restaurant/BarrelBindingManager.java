package com.imc.restaurant;

import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 木桶绑定管理。
 *
 * 流程（trae.md 第1节）：
 *   玩家按 B ▶ 系统在聊天框 log 一个菜名 ▶ 玩家按 I 绑定（看向木桶）
 *   ▶ 再 log 下一个菜名 ▶ 绑定 ……
 *   当绑定了 16 个木桶则结束，并记住 菜名 -> 木桶坐标 的映射。
 */
public class BarrelBindingManager {

    private boolean binding = false;
    private int currentIndex = 0;          // 当前等待绑定的菜在 DISHES 中的下标
    private final Map<String, BlockPos> dishToBarrel = new HashMap<>();
    private final List<BlockPos> boundOrder = new ArrayList<>();

    public boolean isBinding() {
        return binding;
    }

    public int boundCount() {
        return boundOrder.size();
    }

    public Map<String, BlockPos> getBindings() {
        return dishToBarrel;
    }

    /** 玩家按 B：开始绑定流程，报第一个菜名。 */
    public void startBinding(LocalPlayer player) {
        if (binding) {
            IMCRestaurantMod.send(player,Component.literal("§e[IMC] 绑定流程已在进行中，当前菜：§f" + currentDishName()));
            return;
        }
        if (boundOrder.size() >= DishList.DISH_COUNT) {
            IMCRestaurantMod.send(player,Component.literal("§a[IMC] 已绑定 16 个木桶。按 J 重新开始订单流程即可。"));
            return;
        }
        binding = true;
        currentIndex = boundOrder.size();
        announceDish(player);
    }

    /** 玩家按 I：将当前看向的木桶绑定到当前菜名。 */
    public void bindCurrentBarrel(LocalPlayer player) {
        if (!binding) {
            IMCRestaurantMod.send(player,Component.literal("§c[IMC] 当前未处于绑定流程，请先按 B 开始。"));
            return;
        }
        BlockPos pos = getTargetedBarrelPos();
        if (pos == null) {
            IMCRestaurantMod.send(player,Component.literal("§c[IMC] 请对准一个木桶再按 I 绑定。"));
            return;
        }
        String dish = currentDishName();
        // 若该菜已经绑定过（理论不会发生，因为按顺序），覆盖即可
        dishToBarrel.put(dish, pos);
        boundOrder.add(pos);
        IMCRestaurantMod.send(player,Component.literal(
                String.format("§a[IMC] 绑定成功 §f[%d/16]§a：§e%s §a-> §7%s",
                        boundOrder.size(), dish, pos.toShortString())));

        if (boundOrder.size() >= DishList.DISH_COUNT) {
            binding = false;
            currentIndex = DishList.DISH_COUNT;
            IMCRestaurantMod.send(player,Component.literal("§b[IMC] 16 个木桶全部绑定完成！可以按 J 开始订单流程。"));
            return;
        }
        currentIndex = boundOrder.size();
        announceDish(player);
    }

    private void announceDish(LocalPlayer player) {
        if (currentIndex >= DishList.DISHES.size()) {
            return;
        }
        String dish = DishList.DISHES.get(currentIndex);
        IMCRestaurantMod.send(player,Component.literal("§d[IMC] 请对准 §e" + dish + " §d对应的木桶，按 §fI §d绑定。"));
    }

    private String currentDishName() {
        if (currentIndex < 0 || currentIndex >= DishList.DISHES.size()) {
            return "未知";
        }
        return DishList.DISHES.get(currentIndex);
    }

    /** 取得玩家视线准星对准的木桶坐标，非木桶或未对准则返回 null。 */
    private BlockPos getTargetedBarrelPos() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos pos = ((BlockHitResult) mc.hitResult).getBlockPos();
        if (mc.level == null) return null;
        if (!(mc.level.getBlockState(pos).getBlock() instanceof BarrelBlock)) {
            return null;
        }
        BlockEntity be = mc.level.getBlockEntity(pos);
        return (be instanceof BarrelBlockEntity) ? pos : null;
    }

    public void reset() {
        binding = false;
        currentIndex = 0;
        dishToBarrel.clear();
        boundOrder.clear();
    }
}
