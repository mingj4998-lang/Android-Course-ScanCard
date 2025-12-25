# ScanCard（安卓课程大作业：身份证/银行卡 OCR 识别）

 本项目为《移动开发技术》课程大作业。  
 -  **身份证识别 + 银行卡识别** 小应用：通过系统相机拍照，把图片压缩后转为 Base64，调用 **腾讯云 OCR 接口** 获取识别结果，并在界面展示，同时保存最近 5 条识别历史，支持一键复制结果。
---

## 1. 功能概览

### 1.1 主界面入口（MainActivity）
- 主界面两个按钮：
  - 进入 **身份证识别**（`IdCardActivity`）
  - 进入 **银行卡识别**（`BankCardActivity`）

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
---

## 2. 项目结构与核心代码说明
### 2.1 目录结构
- `MainActivity.java`：主入口，跳转到两个识别页面。
- `Tc3Signer.java`：实现腾讯云 **TC3-HMAC-SHA256** 签名算法。
- `TencentOcrClient.java`：生成TC3签名并封装请求体，使用OkHttp库请求腾讯云 OCR 的网络调用。
- `HistoryStore.java`：使用 SharedPreferences 保存/读取最近 5 条历史（JSON 数组）。
- `IdCardActivity.java`：身份证识别界面，负责处理UI交互、权限申请、调用相机将图片存入APP根目录、图片压缩后转base64处理、数据写入SP文件、解析返回结果并展示、信息复制到剪切板以及回看历史记录。
- `BankCardActivity.java`：银行卡识别界面，基本同上。
---

### 2.2 识别整体流程
以身份证识别为例：

1. **检查/申请相机权限**
   - `ContextCompat.checkSelfPermission(...)`
   - `ActivityResultContracts.RequestPermission()`

2. **拍照并保存文件**
   - 在 `getExternalFilesDir("Pictures")/ocr` 下创建图片文件
   - `FileProvider.getUriForFile(...)` 生成安全 `Uri`
   - `ActivityResultContracts.TakePicture()` 启动相机并写入 `photoUri`

3. **后台线程处理**
   - `new Thread(() -> { ... }).start();`
   - 主线程更新通过 `Handler(Looper.getMainLooper())`

4. **图片缩放 + Base64**
   - `decodeBitmapFromUri(uri, 1200, 1200)`
   - `bitmap.compress(JPEG, 80, ...)`
   - `Base64.encodeToString(bytes, Base64.NO_WRAP)`

5. **组装请求体 JSON**
     - `ImageBase64`
     - `CardSide` = `"FRONT"` or `"BACK"`

6. **调用腾讯云 OCR（OkHttp）**
   - `TencentOcrClient.callOcr
   - 内部包含：
     - endpoint：`https://ocr.tencentcloudapi.com/`
     - host：`ocr.tencentcloudapi.com`
     - region：`ap-guangzhou`
     - header：`X-TC-Action / X-TC-Version / X-TC-Timestamp / Authorization` 等

7. **解析响应并展示**
   - 使用 `org.json.JSONObject` 解析
   - 若 `Response.Error` 存在则展示错误信息
   - 正常则展示字段内容 + `RequestId`

8. **保存历史记录（最近 5 条）**
   - `HistoryStore.add(...)`
   - SharedPreferences 中保存 JSON 数组

9. **UI 交互**
   - 识别中显示 `ProgressBar`，并禁用按钮避免重复点击（`setLoading(true/false)`）
   - `复制`按钮使用 `ClipboardManager` 复制 `TextView` 内容
   - 历史列表用 `LinearLayout` 动态添加 `TextView`，点击回看详情

---


## 3. 如何运行项目
### 3.1 运行前配置
1. 在项目根目录复制示例文件：
   - 把 `.env.example` 复制一份，重命名为 `.env`
2. 编辑 `.env`，填入你的真实密钥：
   ```env
   API_KEY=你的_secret_key
   SECRET_ID=你的_secret_id
   
### 3.2 运行步骤
1. `git clone` 本仓库
2. 在 Android Studio 打开项目并 Sync Gradle
3. 按第 3 部分配置 `.env`
4. 运行 App
5. 在主界面选择：
   - 身份证识别 → 选择正/反面 → 拍照 → 等待识别结果
   - 银行卡识别 → 拍照 → 等待识别结果
6. 可点击“复制”复制结果，也可点击历史记录回看

   
### 3.3 运行结果展示
<img width="297" height="412" alt="image" src="https://github.com/user-attachments/assets/7945f266-d43a-4c68-9fa2-fb7a8457338f" />
<img width="271" height="411" alt="image" src="https://github.com/user-attachments/assets/ad6d75b9-7121-4d2f-bcf7-4505f54d25d6" />
<img width="297" height="354" alt="image" src="https://github.com/user-attachments/assets/6f104c1b-478f-44f3-b01d-eca5061709a0" />






