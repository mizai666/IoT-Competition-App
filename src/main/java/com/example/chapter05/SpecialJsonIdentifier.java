package com.example.chapter05;

import org.json.JSONObject;
import java.util.Iterator;
import java.util.regex.Pattern;

public class SpecialJsonIdentifier {

    /**
     * 判断JSON字符串是否为特殊格式
     * 规则：如果所有键名都不以数字结尾，则视为特殊JSON
     * @param jsonStr JSON字符串
     * @return true表示是特殊JSON
     */
    public static boolean isSpecialJson(String jsonStr) {
        if (jsonStr == null || jsonStr.isEmpty()) return false;
        try {
            JSONObject json = new JSONObject(jsonStr);
            Iterator<String> keys = json.keys();
            // 检查是否所有键都以数字结尾
            boolean allHaveNumberSuffix = true;
            while (keys.hasNext()) {
                String key = keys.next();
                if (!Pattern.compile("\\d+$").matcher(key).find()) {
                    allHaveNumberSuffix = false;
                    break;
                }
            }
            // 只有当所有键都有数字后缀时，才不是特殊JSON（即普通多设备JSON）
            return !allHaveNumberSuffix;
        } catch (Exception e) {
            return false;
        }
    }
}