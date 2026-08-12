package com.imc.restaurant;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

/**
 * 按键绑定：
 *  B - 开始绑定木桶 / 开始订单流程
 *  I - 将当前看向的木桶绑定到当前菜名
 *  J - 读取盔甲架订单 / 终止当前流程
 *
 * 按键回调通过 IMCRestaurantMod 处理。
 */
public final class KeyBindings {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(IMCRestaurantMod.MOD_ID, "main"));

    public static KeyMapping startBind;   // B
    public static KeyMapping bindBarrel;  // I
    public static KeyMapping readOrder;   // J

    private KeyBindings() {
    }

    public static void register(Runnable onB, Runnable onI, Runnable onJ) {
        startBind = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.imcrestaurant.start_bind",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                CATEGORY
        ));
        bindBarrel = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.imcrestaurant.bind_barrel",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_I,
                CATEGORY
        ));
        readOrder = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.imcrestaurant.read_order",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // consumeClick 消费一次按下事件，避免长按重复触发
            while (startBind.consumeClick()) {
                onB.run();
            }
            while (bindBarrel.consumeClick()) {
                onI.run();
            }
            while (readOrder.consumeClick()) {
                onJ.run();
            }
        });
    }
}
