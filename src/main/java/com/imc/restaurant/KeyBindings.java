package com.imc.restaurant;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * 按键绑定：
 *  B - 开始绑定木桶 / 开始订单流程
 *  I - 将当前看向的木桶绑定到当前报出的菜名
 *  J - 读取盔甲架订单 / 终止当前流程
 *  P - 打开/关闭状态面板
 *
 * 按键回调通过 IMCRestaurantMod.KeyHandler 处理。
 */
public final class KeyBindings {
    public static final String CATEGORY = "key.imcrestaurant.category";

    public static KeyBinding startBind;   // B
    public static KeyBinding bindBarrel;  // I
    public static KeyBinding readOrder;   // J
    public static KeyBinding openMenu;    // P

    private KeyBindings() {
    }

    public static void register(Runnable onB, Runnable onI, Runnable onJ, Runnable onP) {
        startBind = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.imcrestaurant.start_bind",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                CATEGORY
        ));
        bindBarrel = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.imcrestaurant.bind_barrel",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_I,
                CATEGORY
        ));
        readOrder = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.imcrestaurant.read_order",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                CATEGORY
        ));
        openMenu = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.imcrestaurant.open_menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // wasPressed 消费一次按下事件，避免长按重复触发
            while (startBind.wasPressed()) {
                onB.run();
            }
            while (bindBarrel.wasPressed()) {
                onI.run();
            }
            while (readOrder.wasPressed()) {
                onJ.run();
            }
            while (openMenu.wasPressed()) {
                onP.run();
            }
        });
    }
}
