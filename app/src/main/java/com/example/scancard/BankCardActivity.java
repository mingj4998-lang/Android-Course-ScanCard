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
import android.widget.TextView;
import android.widget.Toast; // 关键逻辑：Toast 提示

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

public class BankCardActivity extends AppCompatActivity {

    private Button btnCamera;               // 拍照
    private ProgressBar progress;           // 加载圈
    private ImageView ivPhoto;              // 预览
    private Button btnCopy;                 // 复制
    private TextView tvResult;              // 结果
    private LinearLayout layoutHistory;     // 历史容器

    private Uri photoUri;                   // 相机输出 Uri
    private final Handler uiHandler = new Handler(Looper.getMainLooper()); // 主线程 handler

    private final ActivityResultLauncher<String> requestCameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    openCameraAndTakePhoto();
                } else {
                    Toast.makeText(this, "未获得相机权限，无法拍照", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<Uri> takePictureLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (success) {
                    ivPhoto.setImageURI(photoUri);
                    startOcrFromUri(photoUri);
                } else {
                    Toast.makeText(this, "拍照取消/失败", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bank_card);

        btnCamera = findViewById(R.id.btn_bank_camera);
        progress = findViewById(R.id.progress_bank);
        ivPhoto = findViewById(R.id.iv_bank_photo);
        btnCopy = findViewById(R.id.btn_bank_copy);
        tvResult = findViewById(R.id.tv_bank_result);
        layoutHistory = findViewById(R.id.layout_bank_history);

        renderHistory();

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
            // 1) 创建保存照片的目录
            File dir = new File(getExternalFilesDir("Pictures"), "ocr"); // Pictures/ocr
            if (!dir.exists()) dir.mkdirs();

            // 2) 创建照片文件名
            File file = new File(dir, "bank_" + System.currentTimeMillis() + ".jpg");

            // 3) 用 FileProvider 把文件转成安全的 Uri
            photoUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    file
            );

            // 4) 启动相机并指定输出位置
            takePictureLauncher.launch(photoUri);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "创建照片Uri失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void startOcrFromUri(@NonNull Uri uri) {
        setLoading(true); // 进入识别中状态
        Toast.makeText(this, "识别中，请稍等...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                // 1) 读取并缩放图片
                Bitmap bitmap = decodeBitmapFromUri(uri, 1200, 1200);
                if (bitmap == null) {
                    uiHandler.post(() -> {
                        setLoading(false);
                        tvResult.setText("读取图片失败：bitmap=null");
                        Toast.makeText(this, "识别失败", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                // 2) 图片转 Base64
                String imageBase64 = bitmapToBase64(bitmap, 80);

                // 3) 拼请求体
                JSONObject body = new JSONObject();
                body.put("ImageBase64", imageBase64);

                // 4) 调腾讯银行卡 OCR
                String respJson = TencentOcrClient.callOcr(
                        "BankCardOCR",
                        "2018-11-19",
                        body.toString()
                );

                // 5) 解析展示
                String display = parseBank(respJson);

                // 6) 写入历史（最近5条）
                String brief = "银行卡：点击回看";
                HistoryStore.add(this, HistoryStore.KEY_BANK, brief, display);

                // 7) 回主线程更新 UI
                uiHandler.post(() -> {
                    setLoading(false);
                    tvResult.setText(display);
                    Toast.makeText(this, "识别完成", Toast.LENGTH_SHORT).show();
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
    }

    private void renderHistory() {
        layoutHistory.removeAllViews(); // 清空容器

        ArrayList<HistoryStore.Item> list = HistoryStore.load(this, HistoryStore.KEY_BANK);

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
            tv.setOnClickListener(v -> {
                tvResult.setText(it.detail);
                Toast.makeText(this, "已打开历史记录", Toast.LENGTH_SHORT).show();
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(0, 0, 0, 10);
            tv.setLayoutParams(lp);

            layoutHistory.addView(tv);
        }
    }

    private String parseBank(String responseJson) {
        try {
            JSONObject root = new JSONObject(responseJson);
            JSONObject resp = root.getJSONObject("Response");

            if (resp.has("Error")) {
                JSONObject err = resp.getJSONObject("Error");
                return "调用失败：\nCode=" + err.optString("Code") +
                        "\nMessage=" + err.optString("Message") +
                        "\nRequestId=" + resp.optString("RequestId");
            }

            String cardNo = resp.optString("CardNo");
            String bankInfo = resp.optString("BankInfo");
            String cardType = resp.optString("CardType");
            String cardName = resp.optString("CardName");
            String validDate = resp.optString("ValidDate");


            StringBuilder sb = new StringBuilder();
            sb.append("银行卡识别结果\n\n");
            if (!cardNo.isEmpty()) sb.append("卡号：").append(cardNo).append("\n");
            if (!bankInfo.isEmpty()) sb.append("银行信息：").append(bankInfo).append("\n");
            if (!cardType.isEmpty()) sb.append("卡类型：").append(cardType).append("\n");
            if (!cardName.isEmpty()) sb.append("卡名称：").append(cardName).append("\n");
            if (!validDate.isEmpty()) sb.append("有效期：").append(validDate).append("\n");

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
