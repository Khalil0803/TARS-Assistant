# TARS 助手（初始版本）

这是一个可通过 GitHub Actions 云端编译的 Android 项目。当前版本包含聊天界面、DeepSeek API Key 本地设置入口、无障碍设置快捷入口，以及一个基础的无障碍服务骨架。

## 获取 APK

1. 将项目文件上传到你的 `TARS-Assistant` 仓库的 `main` 分支，保留 `.github/workflows/android.yml`。
2. 打开仓库的 **Actions** 标签页，选择 **Build Android APK** 工作流。
3. 工作流成功后，在对应运行记录页面底部的 **Artifacts** 下载 `TARS-Assistant-debug-apk`，解压得到 `app-debug.apk`。

## 首次使用

1. 安装 APK，打开右上角设置。
2. 在应用中填写自己的 DeepSeek API Key 并保存。密钥只保存在应用本机偏好设置中；不要提交到仓库。
3. 需要使用无障碍服务时，点“开启无障碍服务”，在系统设置中手动启用“TARS 自动操作服务”。

## 当前限制

- 这是初始可编译骨架，聊天功能通过 DeepSeek API 工作。
- 无障碍服务目前只包含按界面文字查找并点击的基础方法，还没有把自然语言任务解析成完整的自动操作流程，也没有接入截图视觉理解、计划/重试、任务历史、定时任务或本地知识库。
- 公开仓库不要提交 API Key、密码、个人文件或真实聊天记录。
