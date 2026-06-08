# 实验二：文件状态监测

## 概述

本实验实现了一个 Shell 脚本，用于实时监测指定文件的大小变化。脚本会每隔 5 秒检测一次目标文件，并在文件大小发生变化时给出提示。

## 文件说明

```
实验二/
├── monitor.sh    # 文件状态监测脚本
└── README.md     # 本说明文档
```

## 功能描述

### 核心流程

1. **清屏** — 清理终端界面
2. **输入文件名** — 提示用户输入要监测的文件名称
3. **存在性检查** — 若文件不存在则报错并退出
4. **显示初始状态** — 使用 `ls -l` 展示文件的完整信息
5. **循环监测** — 每 5 秒获取一次文件大小并与上一轮比较

### 退出条件（满足其一即退出）

| 条件 | 判定逻辑 | 提示信息 |
|------|----------|----------|
| 大小改变 2 次 | 累计文件大小变化次数 ≥ 2 | `Change number exceeds 2, test end!` |
| 连续 10 次未变 | 连续监测 10 次（共 50 秒）文件大小均无变化 | `Test number exceeds 10!` |
| 用户手动中断 | 按下 `Ctrl+C` | `Monitor stopped by user.` |

## 使用方法

### 1. 赋予执行权限

```bash
chmod +x monitor.sh
```

### 2. 准备测试文件

```bash
echo "hello" > test.txt
```

### 3. 运行脚本

```bash
./monitor.sh
```

根据提示输入文件名（例如 `test.txt`），脚本开始监测。

### 4. 测试示例

在另一个终端中对目标文件进行写入操作来触发大小变化：

```bash
# 终端 1：启动监测
./monitor.sh
# 输入: test.txt

# 终端 2：修改文件触发变化
echo "append some data" >> test.txt       # 第一次变化
echo "append more data" >> test.txt       # 第二次变化 → 触发退出
```

## 技术要点

| 要点 | 说明 |
|------|------|
| 获取文件大小 | 使用 `wc -c < filename` 获取字节数（比 `ls -l \| awk` 更简洁） |
| 定时检测 | `sleep 5` 实现 5 秒间隔 |
| 信号捕获 | `trap ... INT` 捕获 Ctrl+C，实现优雅退出 |
| 只读常量 | `readonly` 声明阈值常量，便于维护 |
| 严格模式 | `set -euo pipefail` 增强脚本健壮性 |

## 可配置参数

脚本顶部定义了三个只读常量，可按需修改：

```bash
readonly MAX_UNCHANGED=10   # 连续未改变上限（次）
readonly MAX_CHANGES=2      # 累计改变上限（次）
readonly INTERVAL=5         # 检测间隔（秒）
```

## 环境要求

- **操作系统**：Linux / macOS / WSL（任意类 Unix 环境）
- **Shell**：Bash 3.0+

（Windows 用户推荐使用 WSL 或 Git Bash 运行）
