# ScanCard（安卓课程大作业：身份证/银行卡 OCR 识别）

 本项目为《移动开发技术》课程大作业。  
 - 我做了一个 **身份证识别 + 银行卡识别** 的小应用：通过系统相机拍照，把图片压缩后转为 Base64，调用 **腾讯云 OCR 接口** 获取识别结果，并在界面展示，同时保存最近 5 条识别历史，支持一键复制结果。
 - 注：课程大作业只要求实现身份证识别，但受同学启发并且在调用腾讯云服务时看到不仅可以识别身份证还能识别银行卡、驾驶证和其他有效证件，于是我做了扩展，在身份证识别代码的基础上稍作修改实现了银行卡信息的识别；
       仓库上传遵循老师要求，**密钥不入库**，使用 `.env` + `.gitignore` 进行屏蔽，代码里通过 `BuildConfig` 读取密钥。
 - 结合课程所学本项目主要综合使用课程内容如下：
  **UI 设计 / 常用控件**：Button、TextView、ImageView、LinearLayout
  **Activity 组件 / Intent 跳转**： `MainActivity` → `IdCardActivity` / `BankCardActivity`
  **Android 存储（SharedPreferences）**： `HistoryStore` 保存最近 5 条历史记录
  **Android 6.0 动态权限**：相机权限申请与处理
  **网络访问**：OkHttp 发起 HTTP POST 请求、JSON（org.json）组装请求体与解析响应

---

## 1. 功能概览

### 1.1 主界面入口（MainActivity）
- 主界面两个按钮：
  - 进入 **身份证识别**（`IdCardActivity`）
  - 进入 **银行卡识别**（`BankCardActivity`）

对应代码：`MainActivity.java` 使用 `Intent` 跳转两个 Activity。

### 1.2 身份证识别（IdCardActivity）
- 动态申请相机权限（`Manifest.permission.CAMERA`）
- 调用系统相机拍照并保存到 App 私有目录（FileProvider + `TakePicture()`）
- 选择身份证正反面（`RadioGroup`）：
  - `"FRONT"` 正面
  - `"BACK"` 反面
- 图片读取并缩放（避免太大）→ JPEG 压缩 → Base64
- 调用腾讯云接口 `IDCardOCR`（version：`2018-11-19`）
- 解析 JSON 结果并显示：
  - 姓名 / 性别 / 民族 / 出生 / 地址 / 身份证号 / 签发机关 / 有效期等
- 保存最近 5 条历史记录（SharedPreferences）
- 支持一键复制识别结果到剪贴板

### 1.3 银行卡识别（BankCardActivity）
流程与身份证识别类似，区别是：
- 调用腾讯云接口 `BankCardOCR`（version：`2018-11-19`）
- 解析展示：
  - 卡号 / 银行信息 / 卡类型 / 卡名称 / 有效期等
- 保存最近 5 条历史记录（SharedPreferences）
- 支持一键复制识别结果到剪贴板

---

## 2. 项目结构与核心代码说明

### 2.1 目录结构
- `MainActivity.java`：主入口，跳转到两个识别页面
- `IdCardActivity.java`：身份证识别流程 + 历史 + 复制
- `BankCardActivity.java`：银行卡识别流程 + 历史 + 复制
- `TencentOcrClient.java`：封装请求腾讯云 OCR 的网络调用（OkHttp）
- `Tc3Signer.java`：实现腾讯云 **TC3-HMAC-SHA256** 签名（生成 Authorization）
- `HistoryStore.java`：使用 SharedPreferences 保存/读取最近 5 条历史（JSON 数组）
注：调用腾讯云OCR请求的代码逻辑请参照网页https://cloud.tencent.com/document/product/866/17597
---

### 2.2 识别整体流程
以身份证识别为例（银行卡同理）：

1. **检查/申请相机权限**
   - `ContextCompat.checkSelfPermission(...)`
   - `ActivityResultContracts.RequestPermission()`

2. **拍照并保存文件**
   - 在 `getExternalFilesDir("Pictures")/ocr` 下创建图片文件
   - `FileProvider.getUriForFile(...)` 生成安全 `Uri`
   - `ActivityResultContracts.TakePicture()` 启动相机并写入 `photoUri`

3. **后台线程处理（避免卡 UI）**
   - `new Thread(() -> { ... }).start();`
   - 主线程更新通过 `Handler(Looper.getMainLooper())`

4. **图片缩放 + Base64**
   - `decodeBitmapFromUri(uri, 1200, 1200)`
   - `bitmap.compress(JPEG, 80, ...)`
   - `Base64.encodeToString(bytes, Base64.NO_WRAP)`

5. **组装请求体 JSON**
   - 身份证：
     - `ImageBase64`
     - `CardSide` = `"FRONT"` or `"BACK"`
   - 银行卡：
     - `ImageBase64`

6. **调用腾讯云 OCR（OkHttp）**
   - `TencentOcrClient.callOcr(action, version, payloadJson)`
   - 内部包含：
     - endpoint：`https://ocr.tencentcloudapi.com/`
     - host：`ocr.tencentcloudapi.com`
     - region：`ap-guangzhou`
     - header：`X-TC-Action / X-TC-Version / X-TC-Timestamp / Authorization` 等

7. **解析响应并展示**
   - 使用 `org.json.JSONObject` 解析
   - 若 `Response.Error` 存在则展示错误信息（包含 Code/Message/RequestId）
   - 正常则展示字段内容 + `RequestId`

8. **保存历史记录（最近 5 条）**
   - `HistoryStore.add(...)`
   - SharedPreferences 中保存 JSON 数组，每条包含：
     - `time`（格式 `MM-dd HH:mm`）
     - `brief`
     - `detail`

9. **UI 交互**
   - 识别中显示 `ProgressBar`，并禁用按钮避免重复点击（`setLoading(true/false)`）
   - `复制`按钮使用 `ClipboardManager` 复制 `TextView` 内容
   - 历史列表用 `LinearLayout` 动态添加 `TextView`，点击回看详情

---

## 3. 密钥管理与屏蔽

### 3.1 原则
- **真实密钥不上传 GitHub**
- 仓库只保留示例文件 `.env.example`
- `.gitignore` 忽略 `.env`、`local.properties`、`build/`、`.idea/` 等本机/缓存文件

### 3.2 本项目中密钥的使用方式
代码中读取的是：

- `TencentOcrClient.java`
  - `private static final String SECRET_ID = BuildConfig.SECRET_ID;`
  - `private static final String SECRET_KEY = BuildConfig.API_KEY;`

也就是说：**项目运行时从 BuildConfig 里拿密钥，而不是把密钥写死在源码里。**

### 3.3 你需要做的配置（运行前）
1. 在项目根目录复制示例文件：
   - 把 `.env.example` 复制一份，重命名为 `.env`

2. 编辑 `.env`，填入你的真实密钥：
   ```env
   API_KEY=你的_secret_key
   SECRET_ID=你的_secret_id

## 4. 如何运行项目

### 4.1 环境准备
- Android Studio课程实验环境版本：
  Android Studio Ladybug | 2024.2.1 Patch 2
  Build #AI-242.23339.11.2421.12550806, built on October 25, 2024
  Runtime version: 21.0.3+-12282718-b509.11 amd64
  VM: OpenJDK 64-Bit Server VM by JetBrains s.r.o.
  Toolkit: sun.awt.windows.WToolkit
  Windows 11.0
  GC: G1 Young Generation, G1 Concurrent GC, G1 Old Generation
  Memory: 2048M
  Cores: 16
  Registry:
    ide.experimental.ui=true
    i18n.locale=

- 设备：真机或模拟器（需要相机能力；模拟器可用虚拟相机/导入图片）
### 4.1 运行结果展示(仅展示身份证界面)
![ea1b0dfa014d18f341f48271ff831e25](https://github.com/user-attachments/assets/08c596f7-8a00-41c4-b147-0000ac7600c7)
![qq_pic_merged_1766505138152](https://github.com/user-attachments/assets/b80997d0-d039-454a-ac79-a4125bf3b1e7)
![3b781eca4847a200f9909ff7a01d2296](https://github.com/user-attachments/assets/cb5b77f7-9dc2-47de-b750-c66f4a8ff0c7)



### 4.2 运行步骤
1. `git clone` 本仓库
2. 在 Android Studio 打开项目并 Sync Gradle
3. 按第 3 部分配置 `.env`
4. 运行 App
5. 在主界面选择：
   - 身份证识别 → 选择正/反面 → 拍照 → 等待识别结果
   - 银行卡识别 → 拍照 → 等待识别结果
6. 可点击“复制”复制结果，也可点击历史记录回看

## 5. 可能遇到的问题（我踩过的坑）
1. **GitHub 上传时别把 `.env` 提交**
   - 检查 `git status`，确保仓库里只有 `.env.example`，没有 `.env`

2. **BuildConfig 字段爆红**
   - 一般是 Gradle 未 sync，或 `buildConfigField` 配置未生效
   - 重新 Sync / Rebuild 后再运行

3. **拍照失败**
   - 需要在 Manifest 和 FileProvider 配置正确（本项目使用 `FileProvider.getUriForFile(...)`）
   - 确认设备/模拟器有相机支持


