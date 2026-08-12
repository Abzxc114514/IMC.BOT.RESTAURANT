package com.imc.restaurant;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 订单读取（trae.md 第2节）。
 *
 * 玩家按 J：
 *   - 获取最近的盔甲架列表，读取它们的（自定义）名字。
 *   - 名字属于"订单信息"的盔甲架（非菜单菜名）则排除。
 *   - 剩下的名字即为订单菜名，记住它们，供后续取餐使用。
 *
 * 判断"订单信息盔甲架"的规则：
 *   - 名字为空 / "订单" / 含 "order"/"info"/"信息" 等关键字。
 *   - 名字不在菜单 DishList 中的，统一视为订单信息，排除。
 *   反之，名字能在菜单中匹配到的，视为本次订单需要交付的菜名。
 */
public class OrderManager {

    private static final double SCAN_RADIUS = 3.0;

    private final List<String> currentOrder = new ArrayList<>();

    public List<String> getCurrentOrder() {
        return currentOrder;
    }

    public boolean hasOrder() {
        return !currentOrder.isEmpty();
    }

    /**
     * 扫描附近盔甲架，解析出订单菜名。
     * @return 是否成功读取到至少一个菜名。
     */
    public boolean readOrder(LocalPlayer player) {
        currentOrder.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            IMCRestaurantMod.send(player,Component.literal("§c[IMC] 世界未加载，无法读取订单。"));
            return false;
        }

        Vec3 eye = player.getEyePosition();
        AABB box = new AABB(
                eye.x - SCAN_RADIUS, eye.y - SCAN_RADIUS, eye.z - SCAN_RADIUS,
                eye.x + SCAN_RADIUS, eye.y + SCAN_RADIUS, eye.z + SCAN_RADIUS);

        List<ArmorStand> stands = new ArrayList<>();
        for (Entity e : mc.level.getEntities(player, box)) {
            if (e instanceof ArmorStand as) {
                stands.add(as);
            }
        }

        if (stands.isEmpty()) {
            IMCRestaurantMod.send(player,Component.literal("§c[IMC] 附近没有盔甲架。"));
            return false;
        }

        for (ArmorStand as : stands) {
            String name = getCustomName(as);
            if (name == null || name.isBlank()) {
                continue; // 无名盔甲架视为订单信息/装饰，排除
            }
            String trimmed = name.trim();
            // 名字命中菜单 → 订单菜名
            if (DishList.isDish(trimmed)) {
                currentOrder.add(trimmed);
            } else {
                // 非菜单名字 → 视为订单信息盔甲架，排除
            }
        }

        if (currentOrder.isEmpty()) {
            IMCRestaurantMod.send(player,Component.literal("§e[IMC] 未识别到任何菜单菜名（已排除订单信息盔甲架）。"));
            return false;
        }

        IMCRestaurantMod.send(player,Component.literal(
                "§a[IMC] 读取到订单 §f(" + currentOrder.size() + " 道)§a：§e"
                        + String.join("、", currentOrder)));
        return true;
    }

    public void clear() {
        currentOrder.clear();
    }

    private static String getCustomName(ArmorStand as) {
        Component custom = as.getCustomName();
        if (custom == null) return null;
        return custom.getString();
    }
}
