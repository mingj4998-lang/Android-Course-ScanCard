package com.example.scancard;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class TencentOcrClient {

    private static final String SECRET_ID = BuildConfig.SECRET_ID;
    private static final String SECRET_KEY = BuildConfig.API_KEY;

    private static final String HOST = "ocr.tencentcloudapi.com";
    private static final String ENDPOINT = "https://" + HOST + "/";
    private static final String SERVICE = "ocr";
    private static final String CONTENT_TYPE = "application/json; charset=utf-8";

    private static final OkHttpClient client = new OkHttpClient();

    public static String callOcr(String action, String version, String payloadJson) throws Exception {
        long timestamp = System.currentTimeMillis() / 1000; // 秒级时间戳

        // 生成 TC3 签名 Authorization
        String authorization = Tc3Signer.buildAuthorization(
                SECRET_ID,
                SECRET_KEY,
                SERVICE,
                HOST,
                action,
                payloadJson,
                timestamp
        );

        MediaType mediaType = MediaType.parse(CONTENT_TYPE);
        RequestBody body = RequestBody.create(payloadJson, mediaType);

        Request request = new Request.Builder()
                .url(ENDPOINT)
                .post(body)
                .addHeader("Content-Type", CONTENT_TYPE)
                .addHeader("Host", HOST)
                .addHeader("X-TC-Action", action)
                .addHeader("X-TC-Version", version)
                .addHeader("X-TC-Timestamp", String.valueOf(timestamp))
                .addHeader("X-TC-Region", "ap-guangzhou")
                .addHeader("Authorization", authorization)
                .build();

        Response response = client.newCall(request).execute();
        String respStr = response.body() != null ? response.body().string() : "";
        response.close();
        return respStr;
    }
}
