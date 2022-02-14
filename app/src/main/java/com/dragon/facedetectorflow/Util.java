package com.dragon.facedetectorflow;

import android.content.Context;
import android.util.Base64;

import java.util.UUID;

public class Util {

    //在这边填写 API_KEY 和 API_SECRET
    public static String API_KEY = BuildConfig.API_KEY;
    public static String API_SECRET = BuildConfig.API_SECRET;

    public static String CN_LICENSE_URL = "https://api-cn.faceplusplus.com/sdk/v3/auth";
    public static String TEST_LICENSE_URL = "";
    public static String US_LICENSE_URL = "https://api-us.faceplusplus.com/sdk/v3/auth";

    public static String getUUIDString(Context mContext) {
        String KEY_UUID = "key_uuid";
        SharedUtil sharedUtil = new SharedUtil(mContext);
        String uuid = sharedUtil.getStringValueByKey(KEY_UUID);
        if (uuid != null && uuid.trim().length() != 0)
            return uuid;

        uuid = UUID.randomUUID().toString();
        uuid = Base64.encodeToString(uuid.getBytes(),
                Base64.DEFAULT);

        sharedUtil.saveStringValue(KEY_UUID, uuid);
        return uuid;
    }
}