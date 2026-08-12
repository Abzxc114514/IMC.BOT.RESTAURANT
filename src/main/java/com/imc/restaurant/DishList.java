package com.imc.restaurant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 餐厅菜单 - 16道菜。
 * 顺序与 trae.md 中所列一致。
 */
public final class DishList {
    public static final int DISH_COUNT = 16;

    public static final List<String> DISHES;

    static {
        List<String> list = new ArrayList<>();
        list.add("炸鸡排");
        list.add("烤包子");
        list.add("葱花饼");
        list.add("炸猪排");
        list.add("炸鳕鱼");
        list.add("中式汉堡");
        list.add("薯条");
        list.add("炸鱼薯条");
        list.add("鳕鱼饺子");
        list.add("韭菜鸡蛋饺子");
        list.add("猪肉白菜饺子");
        list.add("猪肉大葱饺子");
        list.add("韭菜炒鸡蛋");
        list.add("羊肉饺子");
        list.add("牛肉饺子");
        list.add("葱爆羊肉");
        DISHES = Collections.unmodifiableList(list);
    }

    private DishList() {
    }

    /** 返回菜单中是否包含指定菜名（精确匹配）。 */
    public static boolean isDish(String name) {
        if (name == null) return false;
        return DISHES.contains(name);
    }
}
