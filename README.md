# IMC.BOT.RESTAURANT

> IMC 饺子店 bot —— Minecraft 1.21.4 Fabric 自动化餐厅经营模组

一个面向 IMC 饺子店场景的客户端自动化模组。玩家通过绑定木桶到菜品、读取盔甲架订单，由 bot 自动完成「转头取餐 → 放入热栏 → 瞄准生物 → 喂食」的完整交付流程。

---

## 功能特性

- **木桶绑定**：将 16 道菜逐个绑定到对应的木桶，系统在聊天框按菜单顺序报菜名引导绑定
- **订单识别**：扫描附近盔甲架的自定义名称，自动区分「订单信息架」与「菜名订单」
- **自动取餐**：根据订单菜名转头对准木桶、打开、shift 转移物品到背包
- **自动喂食**：对准最近的生物（除玩家、盔甲架），切换热栏 1~5 并右键投喂
- **流程循环**：一道菜交付完自动继续下一道，全部完成后可重新读取订单

---

## 环境要求

| 组件 | 版本 |
|------|------|
| Minecraft | 1.21.4 |
| Fabric Loader | ≥ 0.16.9 |
| Fabric API | 0.110.5+1.21.4 |
| Java | 21（运行 & 构建） |

> 本模组为 **客户端模组**（`environment: client`），无需服务端安装。

---

## 安装

1. 安装 [Fabric Loader](https://fabricmc.net/use/) 0.16.9 或更高版本
2. 下载 [Fabric API](https://modrinth.com/mod/fabric-api) 并放入 `mods` 文件夹
3. 从 [Releases](https://github.com/Abzxc114514/IMC.BOT.RESTAURANT/releases) 下载 `imcrestaurant-1.0.0.jar`，放入 `mods` 文件夹
4. 启动 Minecraft 1.21.4

---

## 按键说明

| 按键 | 功能 |
|------|------|
| **B** | 开始木桶绑定流程（系统报第一个菜名） |
| **I** | 将当前看向的木桶绑定到当前菜名 |
| **J** | 启动自动流程（读订单→取餐→喂食）；运行中按 J 终止 |

> 按键可在游戏内 `选项 → 控制 → 按键绑定 → IMC餐厅` 中自定义。

---

## 使用流程

### 第一步：绑定木桶（B / I）

1. 按 **B** 开始绑定，聊天框会提示第一道菜名（炸鸡排）
2. 对准对应菜品的木桶，按 **I** 绑定
3. 系统自动报下一道菜名，重复按 **I** 绑定
4. 绑满 **16 个木桶**后流程自动结束，聊天框提示完成

绑定顺序固定为以下菜单：

```
1.  炸鸡排         9.  鳕鱼饺子
2.  烤包子         10. 韭菜鸡蛋饺子
3.  葱花饼         11. 猪肉白菜饺子
4.  炸猪排         12. 猪肉大葱饺子
5.  炸鳕鱼         13. 韭菜炒鸡蛋
6.  中式汉堡       14. 羊肉饺子
7.  薯条           15. 牛肉饺子
8.  炸鱼薯条       16. 葱爆羊肉
```

### 第二步：启动自动交付（J）

按 **J** 后，模组依次执行：

1. **读取订单**：扫描玩家半径 **3 格**内的盔甲架
   - 名字命中菜单的 → 视为订单菜名
   - 名字不在菜单中（订单信息架/装饰） → 自动排除
2. **逐道取餐**（每道菜循环）：
   - 转头对准该菜对应的木桶
   - 右键打开木桶
   - shift 点击容器内物品，转移到玩家背包
   - 关闭木桶界面
3. **瞄准生物**：扫描附近最近的生物（排除玩家自身与盔甲架），转头对准
4. **喂食**：切换热栏 1~5 槽位，每切一次先右键再切换，依次投喂
5. 一道菜完成后自动进入下一道，全部完成则提示重新按 J

### 终止流程

自动交付运行中按 **J** 可随时终止，回到空闲状态并关闭已打开的界面。

---

## 项目结构

```
src/main/java/com/imc/restaurant/
├── IMCRestaurantMod.java        # 客户端入口，注册按键 & tick 驱动
├── KeyBindings.java             # B / I / J 按键绑定
├── DishList.java                # 16 道菜菜单常量
├── BarrelBindingManager.java    # 木桶绑定流程管理
├── OrderManager.java            # 盔甲架订单读取
└── AutomationController.java    # 取餐→喂食状态机

src/main/resources/
├── fabric.mod.json              # 模组元数据
├── imcrestaurant.mixins.json    # Mixin 配置
└── assets/imcrestaurant/lang/
    ├── en_us.json               # 英文按键提示
    └── zh_cn.json               # 中文按键提示
```

---

## 从源码构建

### 前置

- JDK 21（推荐 Temurin 21）
- 网络可访问 [Fabric Maven](https://maven.fabricmc.net/) 与 Maven Central

### 步骤

```bash
# 赋予执行权限（首次）
chmod +x ./gradlew

# 构建
./gradlew build
```

构建产物位于 `build/libs/`：

- `imcrestaurant-1.0.0.jar` — 模组主文件
- `imcrestaurant-1.0.0-sources.jar` — 源码包

> 国内网络如遇 Maven Central 限流（429），`build.gradle` 已配置阿里云镜像兜底。

---

## 技术实现

- **状态机驱动**：`AutomationController` 以 tick 为单位驱动 `IDLE → TURN_TO_BARREL → OPEN_BARREL → TAKE_ITEMS → CLOSE_BARREL → AIM_MONSTER → FEED → DONE` 状态流转，每个动作间用 `waitTicks` 等待服务端响应
- **准星检测**：通过 `MinecraftClient.crosshairTarget` 判断玩家是否对准木桶
- **容器操作**：通过 `ClientPlayerInteractionManager.clickSlot` 以 `QUICK_MOVE`（shift 点击）将木桶物品转移到玩家背包
- **实体扫描**：用 `World.getOtherEntities` + 包围盒查询附近盔甲架与生物
- **转头控制**：直接设置玩家 `yaw/pitch/headYaw/bodyYaw` 实现瞬时转向

---

## 许可证

MIT
