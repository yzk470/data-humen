# Live2D 模型目录

## 部署步骤

1. 从 https://www.live2d.com/download/cubism-sdk/ 下载 Cubism SDK for Web
2. 解压后，将 `Samples/Resources/Haru/` 全部复制到 `base/` 目录
3. 打开 `base/haru.2048/texture_00.png`，标记面部矩形坐标
4. 更新 `application.yml` 中 `app.avatar.face-region` 为实际坐标
5. 运行以下命令创建默认形象：

```bash
mkdir -p generated/avatar_default
cp -r base/* generated/avatar_default/
echo '{"id":"avatar_default","name":"默认形象","createdAt":"2026-06-09T00:00:00"}' > generated/avatar_default/meta.json
```

## 目录结构

```
models/
├── base/                    # 基础 Live2D 模型（Haru/Mao 示例）
│   ├── haru.model3.json
│   ├── haru.physics3.json
│   ├── haru.pose3.json
│   ├── haru.2048/
│   │   └── texture_00.png  # 纹理图集
│   └── expressions/
└── generated/               # 生成的形象（自动管理）
    └── avatar_default/      # 默认形象
```
