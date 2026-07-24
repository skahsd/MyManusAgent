package cn.mymanus.manus.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;

/**
 * 用于从字符串中查找第一个合法的 JSON 对象或数组。
 */
public class JsonFinder {

    /**
     * Jackson 的 ObjectMapper 实例，用于验证候选 JSON 字符串是否合法。
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 从给定字符串中查找第一个合法的 JSON 对象或数组。
     *
     * @param src 输入字符串
     * @return 包含第一个合法 JSON 的 Optional，若未找到则返回空 Optional
     */
    public static Optional<String> findFirstJson(String src) {
        for (int i = 0; i < src.length(); i++) {
            char startChar = src.charAt(i);
            if (startChar == '{' || startChar == '[') {
                char endChar = (startChar == '{') ? '}' : ']';
                // 遍历后续的所有结束符位置
                for (int j = i + 1; j < src.length(); j++) {
                    if (src.charAt(j) == endChar) {
                        String candidate = src.substring(i, j + 1);
                        if (isValidJson(candidate)) {
                            return Optional.of(candidate);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * 验证给定字符串是否是合法的 JSON 格式。
     *
     * @param candidate 待验证的 JSON 字符串
     * @return 如果是合法 JSON 返回 true，否则返回 false
     */
    private static boolean isValidJson(String candidate) {
        try {
            MAPPER.readTree(candidate);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }
}
