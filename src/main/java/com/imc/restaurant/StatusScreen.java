package com.imc.restaurant;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * P 键状态面板（trae.md / NewModTraeLookMe.md 第1.3节：按 P 打开 GUI）。
 *
 * 修复"菜单模糊"：不使用默认的背景模糊（renderBackground 的世界模糊），
 * 改为绘制实心半透明黑底，使文字清晰可读。
 *
 * 面板内容：
 *  - 木桶绑定进度（已绑定 / 16，当前待绑菜名）
 *  - 自动流程状态（是否运行、当前菜、剩余订单数）
 *  - 已绑定木桶列表（菜名 -> 坐标）
 *  - 操作说明（B/I/J/P）
 */
public class StatusScreen extends Screen {

    private final BarrelBindingManager barrelManager;
    private final OrderManager orderManager;
    private final AutomationController automation;

    public StatusScreen(BarrelBindingManager bm, OrderManager om, AutomationController ac) {
        super(Text.literal("IMC 餐厅 - 状态面板"));
        this.barrelManager = bm;
        this.orderManager = om;
        this.automation = ac;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 实心半透明黑底：不调用 super.render，避免默认背景模糊
        context.fill(0, 0, this.width, this.height, 0xB0000000);

        TextRenderer t = this.textRenderer;
        int lineH = t.fontHeight + 4;
        int x = 20;
        int y = 18;

        // 标题
        context.drawTextWithShadow(t, Text.literal("§l§9IMC 餐厅 · 状态面板"), x, y, 0xFFFFFF);
        y += lineH + 2;
        context.drawTextWithShadow(t, Text.literal("§8§m----------------------------------------"), x, y, 0x888888);
        y += lineH;

        // 绑定状态
        int bound = barrelManager.boundCount();
        context.drawTextWithShadow(t, Text.literal(
                "§b[绑定] §f已绑定 §e" + bound + "§f/§e16 §f个木桶"), x, y, 0xFFFFFF);
        y += lineH;
        if (barrelManager.isBinding()) {
            String cur = barrelManager.getCurrentBindingDish();
            context.drawTextWithShadow(t, Text.literal(
                    "§b[绑定] §f当前待绑菜名：§e" + (cur == null ? "未知" : cur)), x, y, 0xFFFFFF);
            y += lineH;
            context.drawTextWithShadow(t, Text.literal(
                    "§7  对准木桶按 §fI §7绑定"), x, y, 0x999999);
        } else if (bound >= 16) {
            context.drawTextWithShadow(t, Text.literal(
                    "§a[绑定] §f16 个木桶全部绑定完成，按 §eJ §f开始流程"), x, y, 0xFFFFFF);
        } else {
            context.drawTextWithShadow(t, Text.literal(
                    "§7[绑定] §7未进行绑定，按 §fB §7开始"), x, y, 0x999999);
        }
        y += lineH + 4;

        // 自动流程状态
        if (automation.isRunning()) {
            context.drawTextWithShadow(t, Text.literal(
                    "§a[流程] §f运行中"), x, y, 0xFFFFFF);
            y += lineH;
            String cur = automation.getCurrentDish();
            context.drawTextWithShadow(t, Text.literal(
                    "§a[流程] §f当前菜：§e" + (cur == null ? "—" : cur)), x, y, 0xFFFFFF);
            y += lineH;
            context.drawTextWithShadow(t, Text.literal(
                    "§a[流程] §f剩余订单：§e" + automation.getPendingCount() + " §f道"), x, y, 0xFFFFFF);
            y += lineH;
            context.drawTextWithShadow(t, Text.literal(
                    "§7  按 §fJ §7终止流程"), x, y, 0x999999);
        } else {
            context.drawTextWithShadow(t, Text.literal(
                    "§7[流程] §7未运行，按 §fJ §7启动（需先完成绑定与读取订单）"), x, y, 0x999999);
        }
        y += lineH + 4;

        // 当前订单
        List<String> order = orderManager.getCurrentOrder();
        if (!order.isEmpty()) {
            context.drawTextWithShadow(t, Text.literal(
                    "§d[订单] §f共 §e" + order.size() + " §f道：§b"
                            + String.join("、", order)), x, y, 0xFFFFFF);
        } else {
            context.drawTextWithShadow(t, Text.literal(
                    "§7[订单] §7暂无订单（按 J 读取盔甲架订单）"), x, y, 0x999999);
        }
        y += lineH + 4;

        // 已绑定木桶列表
        context.drawTextWithShadow(t, Text.literal("§6[已绑木桶]"), x, y, 0xFFFFFF);
        y += lineH;
        Map<String, BlockPos> bindings = barrelManager.getBindings();
        if (bindings.isEmpty()) {
            context.drawTextWithShadow(t, Text.literal("§7  无"), x + 10, y, 0x999999);
            y += lineH;
        } else {
            List<String> dishes = new ArrayList<>(bindings.keySet());
            for (int i = 0; i < dishes.size(); i++) {
                String d = dishes.get(i);
                BlockPos p = bindings.get(d);
                String posStr = p == null ? "?" : p.toShortString();
                context.drawTextWithShadow(t, Text.literal(
                        "§7" + (i + 1) + ". §f" + d + " §7-> §8" + posStr), x + 10, y, 0xCCCCCC);
                y += lineH;
            }
        }
        y += 4;

        // 操作说明
        context.drawTextWithShadow(t, Text.literal("§8§m----------------------------------------"), x, y, 0x888888);
        y += lineH;
        context.drawTextWithShadow(t, Text.literal(
                "§f操作：§eB §f开始绑定 §7| §eI §f绑定木桶 §7| §eJ §f启动/终止 §7| §eP/Esc §f关闭"), x, y, 0xFFFFFF);
    }

    @Override
    public boolean shouldPause() {
        // 不暂停游戏，方便在自动流程运行时查看状态
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scancode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_P) {
            this.close();
            return true;
        }
        return super.keyPressed(keyCode, scancode, modifiers);
    }
}
