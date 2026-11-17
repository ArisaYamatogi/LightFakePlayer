# 构建说明

## 环境要求

- JDK 17 或更高版本（推荐 JDK 21）
- Maven 3.8+
- 可访问 Maven Central / JitPack / GitHub 的网络

插件本体（`fakeplayer-core`、`fakeplayer-api`）使用 Java 17 编译，
因此 1.20.x 服务端可以在 Java 17 上运行；1.21+ 服务端本身要求 Java 21。

## 一键构建

在 Git Bash 中执行：

```bash
bash build.sh
```

脚本会：

1. 编译 `fakeplayer-api` 和 `fakeplayer-core`（target 17）
2. 从 GitHub Release 下载上游 `fakeplayer-0.3.19.jar` 到 `.build-cache/`
3. 取出其中的多版本 NMS bridge 类（`v1_20_1` ~ `v1_21_10`）
4. 用本仓库编译出的 core/api 类与配置替换上游 core/api
5. 生成最终插件：`target/fakeplayer-custom.jar`

把 `target/fakeplayer-custom.jar` 放入服务端 `plugins/` 目录即可。
构建脚本只读取上游发行包中的 NMS 桥接类，不会修改上游仓库。

## 为什么复用上游 NMS jar

Mojang 不允许公开分发 remapped NMS jar，上游项目通过 BuildTools 构建各版本桥接类。
本定制版没有修改 `fakeplayer-api` 的 SPI 签名，因此可以直接复用上游发行包中的
`v1_*` 桥接类，避免在每台构建机上下载并编译全部 15 个 NMS 版本。

## 前置插件

- `CommandAPI` 11.0.0（必需）
- `AuthMe`（可选，启用自动注册/登录时需要）
- `PlaceholderAPI`（可选，计时扣费命令中的 PAPI 变量会通过反射解析）
- `LuckPerms` / `Vault` 不强制依赖

## 测试环境

本次定制已在以下环境完成启动与核心功能测试：

- Leaves 1.21.8
- Java 21 & 25
- CommandAPI 11.0.0
- AuthMe 5.7.0

测试内容：`/fp spawn <id>` 命名校验、玩家归属隔离、AuthMe 自动注册登录、
主手/副手保留与背包清理、计时扣费命令、`/fp list` 仅显示自己的假人。
