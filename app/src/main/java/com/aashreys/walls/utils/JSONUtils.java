/*
 * Copyright {2017} {Aashrey Kamal Sharma}
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.aashreys.walls.utils;

import android.support.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by aashreys on 03/12/16.
 */

public class JSONUtils {

    public static String getString(JSONObject object, String key) throws JSONException {
        String value = object.getString(key);
        return stringIsNotEqualsTo(value, null, "", "null") ? value : null;
    }

    public static String optString(JSONObject object, String key, String fallback) {
        String value = null;
        try {
            value = object.getString(key);
        } catch (Exception ignored) {}
        return stringIsNotEqualsTo(value, null, "", "null") ? value : fallback;
    }

    private static boolean stringIsNotEqualsTo(@Nullable String string, String... values) {
        for (String value : values) {
            if (string == null && value == null) {
                return false;
            }
            if (string != null && string.equals(value)) {
                return false;
            }
        }
        return true;
    }

}
