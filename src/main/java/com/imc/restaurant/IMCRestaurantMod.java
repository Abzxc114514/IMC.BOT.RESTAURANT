package com.imc.restaurant;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IMCRestaurant 客户端入口。
 *
 * 按键：
 *   B - 开始/继续木桶绑定流程（trae.md 第1节）
 *   I - 绑定当前看向的木桶到当前菜名
 *   J - 启动自动流程（读取订单 -> 取餐 -> 喂食），或终止正在进行的流程（第7节）
 *
 * 详细流程见 trae.md。
 */
public class IMCRestaurantMod implements ClientModInitializer {
    public static final String MOD_ID = "imcrestaurant";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static IMCRestaurantMod instance;

    private BarrelBindingManager barrelManager;
    private OrderManager orderManager;
    private AutomationController automation;

    @Override
    public void onInitializeClient() {
        instance = this;
        barrelManager = new BarrelBindingManager();
        orderManager = new OrderManager();
        automation = new AutomationController(barrelManager, orderManager);

        KeyBindings.register(
                this::onPressB,
                this::onPressI,
                this::onPressJ
        );

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        LOGGER.info("[IMCRestaurant] 已加载。B=开始绑定 I=绑定木桶 J=读取订单/启动自动流程");
    }

    public static IMCRestaurantMod getInstance() {
        return instance;
    }

    private void onPressB() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        barrelManager.startBinding(player);
    }

    private void onPressI() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        barrelManager.bindCurrentBarrel(player);
    }

    private void onPressJ() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        if (automation.isRunning()) {
            automation.stop(player);
        } else {
            automation.start(player);
        }
    }

    private void onClientTick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) return;
        automation.tick(player);
    }

    public BarrelBindingManager getBarrelManager() {
        return barrelManager;
    }

    public OrderManager getOrderManager() {
        return orderManager;
    }

    public AutomationController getAutomation() {
        return automation;
    }

    @SuppressWarnings("unused")
    public static void msg(LocalPlayer player, String text) {
        if (player != null) {
            player.displayClientMessage(Component.literal(text), false);
        }
    }

    /** 向玩家发送聊天框消息（displayClientMessage 的便捷封装）。 */
    public static void send(LocalPlayer player, Component component) {
        if (player != null) {
            player.displayClientMessage(component, false);
        }
    }
}
