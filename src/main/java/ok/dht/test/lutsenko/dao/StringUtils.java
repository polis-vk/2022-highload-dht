package ok.dht.test.lutsenko.dao;

import one.nio.util.Utf8;

public final class StringUtils {

    private StringUtils() {
    }

    public static String preprocess(String str) {
        if (str == null) {
            return null;
        }
        int i = -1;
        for (int j = 0; j < str.length(); j++) {
            char c = str.charAt(j);
            if (c == '\n' || c == '\\') {
                i = j;
                break;
            }
        }
        if (i == -1) {
            return str;
        }
        StringBuilder stringBuilder = new StringBuilder(str.substring(0, i));
        while (i < str.length()) {
            char c = str.charAt(i);
            if (c == '\\') {
                stringBuilder.append("\\\\");
            } else if (c == '\n') {
                stringBuilder.append("\\n");
            } else {
                stringBuilder.append(c);
            }
            i++;
        }
        return stringBuilder.toString();
    }

    public static String postprocess(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        int i = 0;
        for (byte b : bytes) {
            if (b == (byte)'\\') {
                break;
            }
            i++;
        }
        if (i == bytes.length) {
            return Utf8.toString(bytes);
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (int j = 0; j < i; j++) {
            stringBuilder.append((char) bytes[j]);
        }
        while (i < bytes.length) {
            while (i < bytes.length && bytes[i] != '\\') {
                stringBuilder.append((char)bytes[i]);
                i++;
            }
            if (i < bytes.length - 1) {
                // Все слэши парные, поэтому после слэша гарантированно идет 'n' или еще один слэш
                stringBuilder.append(bytes[i + 1] == (byte)'n' ? '\n' : '\\');
                i += 2;
            }
        }
        return stringBuilder.toString();
    }
}
