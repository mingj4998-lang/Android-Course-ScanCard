package com.example.scancard;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;

public class IdCardActivity extends AppCompatActivity {

    private RadioGroup rgSide;              // 正反面选择
    private Button btnCamera;               // 拍照
    private ProgressBar progress;           // 加载圈
    private ImageView ivPhoto;              // 预览
    private Button btnCopy;                 // 复制
    private TextView tvResult;              // 结果
    private LinearLayout layoutHistory;     // 历史容器

    private Uri photoUri;                   // 相机输出 Uri
    private final Handler uiHandler = new Handler(Looper.getMainLooper()); // 主线程 handler

    // 申请相机权限
    private final ActivityResultLauncher<String> requestCameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    openCameraAndTakePhoto();
                } else {
                    Toast.makeText(this, "未获得相机权限，无法拍照", Toast.LENGTH_SHORT).show();
                }
            });

    // 拍照：系统把照片写入 photoUri，并回调 success
    private final ActivityResultLauncher<Uri> takePictureLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (success) {
                    ivPhoto.setImageURI(photoUri);     // 显示预览
                    startOcrFromUri(photoUri);         // 开始识别
                } else {
                    Toast.makeText(this, "拍照取消/失败", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_id_card); // 绑定身份证布局

        rgSide = findViewById(R.id.rg_idcard_side);
        btnCamera = findViewById(R.id.btn_idcard_camera);
        progress = findViewById(R.id.progress_idcard);
        ivPhoto = findViewById(R.id.iv_idcard_photo);
        btnCopy = findViewById(R.id.btn_idcard_copy);
        tvResult = findViewById(R.id.tv_idcard_result);
        layoutHistory = findViewById(R.id.layout_idcard_history);

        renderHistory(); //进入页面加载历史

        // 拍照按钮：先检查权限，再拍照
        btnCamera.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                openCameraAndTakePhoto();
            } else {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
            }
        });

        // 复制按钮
        btnCopy.setOnClickListener(v -> {
            String text = tvResult.getText().toString();
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("OCR Result", text));
            Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * 作用：创建 photoUri -> 启动相机 -> 把照片保存到这个位置
     */
    private void openCameraAndTakePhoto() {
        try {
            // 1) 创建保存照片的目录（App 私有目录，不需要读写权限）
            File dir = new File(getExternalFilesDir("Pictures"), "ocr"); // Pictures/ocr
            if (!dir.exists()) dir.mkdirs();

            // 2) 创建照片文件名（时间戳避免重名）
            File file = new File(dir, "idcard_" + System.currentTimeMillis() + ".jpg");

            // 3) 用 FileProvider 把文件转成安全的 Uri（给相机用）
            photoUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    file
            );

            // 4) 启动相机并指定输出位置（相机把照片写入 photoUri）
            takePictureLauncher.launch(photoUri);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "创建照片Uri失败", Toast.LENGTH_SHORT).show();
        }
    }

    private String getCardSideOnce() {
        int checkedId = rgSide.getCheckedRadioButtonId();
        return (checkedId == R.id.rb_idcard_back) ? "BACK" : "FRONT";
    }

    private void startOcrFromUri(@NonNull Uri uri) {
        setLoading(true); // 进入识别中状态

        String cardSide = getCardSideOnce();

        new Thread(() -> {
            try {
                // 1) 读取并压缩图片，避免太大
                Bitmap bitmap = decodeBitmapFromUri(uri, 1200, 1200);
                if (bitmap == null) {
                    uiHandler.post(() -> {
                        setLoading(false);
                        tvResult.setText("读取图片失败：bitmap=null");
                        Toast.makeText(this, "识别失败", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                // 2) 转 Base64
                String imageBase64 = bitmapToBase64(bitmap, 80); // JPEG 压缩后 Base64

                // 3) 拼请求体
                JSONObject body = new JSONObject();
                body.put("ImageBase64", imageBase64);
                body.put("CardSide", cardSide);

                // 4) 发请求
                String respJson = TencentOcrClient.callOcr(
                        "IDCardOCR",
                        "2018-11-19",
                        body.toString()
                );

                // 5) 解析展示
                String display = parseIdCard(respJson);

                // 6) 写入历史（最近5条）
                String brief = "身份证(" + cardSide + ")：点击回看";
                HistoryStore.add(this, HistoryStore.KEY_IDCARD, brief, display);

                // 7) 回主线程更新 UI
                uiHandler.post(() -> {
                    setLoading(false);
                    tvResult.setText(display);
                    renderHistory();
                });

            } catch (Exception e) {
                e.printStackTrace();
                uiHandler.post(() -> {
                    setLoading(false);
                    tvResult.setText("识别异常：" + e.getMessage());
                    Toast.makeText(this, "识别失败", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void setLoading(boolean loading) {
        // 识别中显示进度并禁用按钮（防止用户连点多次请求）
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnCamera.setEnabled(!loading);
        btnCopy.setEnabled(!loading);
        rgSide.setEnabled(!loading);
    }

    private void renderHistory() {
        layoutHistory.removeAllViews(); // 清空容器

        ArrayList<HistoryStore.Item> list = HistoryStore.load(this, HistoryStore.KEY_IDCARD);

        if (list.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("暂无历史记录");
            tv.setPadding(12, 12, 12, 12);
            layoutHistory.addView(tv);
            return;
        }

        for (HistoryStore.Item it : list) {
            TextView tv = new TextView(this);
            tv.setPadding(18, 14, 18, 14);
            tv.setBackgroundColor(0xFFEFEFEF);
            tv.setText("[" + it.time + "] " + it.brief);

            // 点击历史回看当时的结果
            tv.setOnClickListener(v -> tvResult.setText(it.detail));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(0, 0, 0, 10);
            tv.setLayoutParams(lp);

            layoutHistory.addView(tv);
        }
    }

    private String parseIdCard(String responseJson) {
        try {
            JSONObject root = new JSONObject(responseJson);
            JSONObject resp = root.getJSONObject("Response");

            if (resp.has("Error")) {
                JSONObject err = resp.getJSONObject("Error");
                return "调用失败：\nCode=" + err.optString("Code") +
                        "\nMessage=" + err.optString("Message") +
                        "\nRequestId=" + resp.optString("RequestId");
            }

            String name = resp.optString("Name");
            String sex = resp.optString("Sex");
            String nation = resp.optString("Nation");
            String birth = resp.optString("Birth");
            String address = resp.optString("Address");
            String idNum = resp.optString("IdNum");
            String authority = resp.optString("Authority");
            String validDate = resp.optString("ValidDate");

            StringBuilder sb = new StringBuilder();
            sb.append("身份证识别结果\n\n");
            if (!name.isEmpty()) sb.append("姓名：").append(name).append("\n");
            if (!sex.isEmpty()) sb.append("性别：").append(sex).append("\n");
            if (!nation.isEmpty()) sb.append("民族：").append(nation).append("\n");
            if (!birth.isEmpty()) sb.append("出生：").append(birth).append("\n");
            if (!address.isEmpty()) sb.append("住址：").append(address).append("\n");
            if (!idNum.isEmpty()) sb.append("身份证号：").append(idNum).append("\n");
            if (!authority.isEmpty()) sb.append("签发机关：").append(authority).append("\n");
            if (!validDate.isEmpty()) sb.append("有效期限：").append(validDate).append("\n");

            sb.append("\nRequestId：").append(resp.optString("RequestId"));
            return sb.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return "解析失败：\n" + responseJson;
        }
    }

    private Bitmap decodeBitmapFromUri(Uri uri, int reqWidth, int reqHeight) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            InputStream is1 = getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(is1, null, opts);
            if (is1 != null) is1.close();

            opts.inSampleSize = calculateInSampleSize(opts, reqWidth, reqHeight);
            opts.inJustDecodeBounds = false;

            InputStream is2 = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(is2, null, opts);
            if (is2 != null) is2.close();
            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;
        while ((height / inSampleSize) > reqHeight || (width / inSampleSize) > reqWidth) {
            inSampleSize *= 2;
        }
        return inSampleSize;
    }

    private String bitmapToBase64(Bitmap bitmap, int jpegQuality) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, baos);
        byte[] bytes = baos.toByteArray();
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }
}
