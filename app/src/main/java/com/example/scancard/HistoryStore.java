package com.example.scancard;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class HistoryStore {

    // SharedPreferences 名称
    private static final String SP_NAME = "ocr_history_sp";

    public static final String KEY_IDCARD = "history_idcard";
    public static final String KEY_BANK = "history_bank";

    // 最多保存 5 条
    private static final int MAX = 5;

    public static class Item {
        public String time;   // 时间
        public String brief;
        public String detail; // 详细结果（点击历史时回看）
    }

    public static ArrayList<Item> load(Context ctx, String key) {
        ArrayList<Item> list = new ArrayList<>();
        try {
            String s = ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
                    .getString(key, "[]");
            JSONArray arr = new JSONArray(s);

            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                Item it = new Item();
                it.time = obj.optString("time");
                it.brief = obj.optString("brief");
                it.detail = obj.optString("detail");
                list.add(it);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public static void add(Context ctx, String key, String brief, String detail) {
        ArrayList<Item> list = load(ctx, key);

        Item it = new Item(); // 创建新记录
        it.time = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                .format(new Date());
        it.brief = brief;
        it.detail = detail;

        list.add(0, it); // 新记录放在最前面

        while (list.size() > MAX) {
            list.remove(list.size() - 1); // 超过 5 条就删最旧的
        }

        save(ctx, key, list);
    }

    private static void save(Context ctx, String key, ArrayList<Item> list) {
        try {
            JSONArray arr = new JSONArray();
            for (Item it : list) {
                JSONObject obj = new JSONObject();
                obj.put("time", it.time);
                obj.put("brief", it.brief);
                obj.put("detail", it.detail);
                arr.put(obj);
            }

            ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(key, arr.toString())
                    .apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
