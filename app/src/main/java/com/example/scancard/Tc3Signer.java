package com.example.scancard;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class Tc3Signer {

    private static final String ALGORITHM = "TC3-HMAC-SHA256";
    private static final String SIGNED_HEADERS = "content-type;host;x-tc-action";
    private static final String CONTENT_TYPE = "application/json; charset=utf-8";

    public static String buildAuthorization(
            String secretId,
            String secretKey,
            String service,
            String host,
            String action,
            String payload,
            long timestamp
    ) throws Exception {

        String date = toUtcDate(timestamp); //UTC 日期

        String canonicalHeaders =
                "content-type:" + CONTENT_TYPE + "\n" +
                        "host:" + host + "\n" +
                        "x-tc-action:" + action.toLowerCase() + "\n";

        String canonicalRequest =
                "POST\n" +
                        "/\n" +
                        "\n" +
                        canonicalHeaders + "\n" +
                        SIGNED_HEADERS + "\n" +
                        sha256Hex(payload);

        String credentialScope = date + "/" + service + "/tc3_request";

        String stringToSign =
                ALGORITHM + "\n" +
                        timestamp + "\n" +
                        credentialScope + "\n" +
                        sha256Hex(canonicalRequest);

        byte[] secretDate = hmacSha256(("TC3" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        byte[] secretService = hmacSha256(secretDate, service);
        byte[] secretSigning = hmacSha256(secretService, "tc3_request");

        String signature = bytesToHex(hmacSha256(secretSigning, stringToSign));

        return ALGORITHM + " " +
                "Credential=" + secretId + "/" + credentialScope + ", " +
                "SignedHeaders=" + SIGNED_HEADERS + ", " +
                "Signature=" + signature;
    }

    private static String toUtcDate(long timestampSeconds) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date(timestampSeconds * 1000));
    }

    private static byte[] hmacSha256(byte[] key, String msg) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(msg.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(d);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(b & 0xff);
            if (hex.length() == 1) sb.append('0');
            sb.append(hex);
        }
        return sb.toString();
    }
}
