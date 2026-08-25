package com.imc.restaurant;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
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
                this::onPressJ,
                this::onPressP
        );

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        LOGGER.info("[IMCRestaurant] 已加载。B=开始绑定 I=绑定木桶 J=读取订单/启动自动流程 P=状态面板");
    }

    public static IMCRestaurantMod getInstance() {
        return instance;
    }

    private void onPressB() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;
        // 绑定流程优先；未绑定时 B 只负责绑定
        barrelManager.startBinding(player);
    }

    private void onPressI() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;
        barrelManager.bindCurrentBarrel(player);
    }

    private void onPressJ() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;
        if (automation.isRunning()) {
            // 第7步：按 J 终止，回到第2步
            automation.stop(player);
        } else {
            // 启动自动流程：读取订单 -> 取餐 -> 喂食
            automation.start(player);
        }
    }

    /** 按 P：打开/关闭状态面板。 */
    private void onPressP() {
        MinecraftClient mc = MinecraftClient.getInstance();
        Screen current = mc.currentScreen;
        if (current instanceof StatusScreen) {
            mc.setScreen(null);
        } else if (mc.player != null) {
            mc.setScreen(new StatusScreen(barrelManager, orderManager, automation));
        }
    }

    private void onClientTick(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
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

    /** 提供给其它代码（或调试）发消息的便利方法。 */
    @SuppressWarnings("unused")
    public static void msg(ClientPlayerEntity player, String text) {
        if (player != null) {
            player.sendMessage(Text.literal(text), false);
        }
    }
}
