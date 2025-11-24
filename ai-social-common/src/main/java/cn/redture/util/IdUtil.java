package cn.redture.util;

import com.github.f4b6a3.ulid.UlidCreator;

public class IdUtil {
    // 使用 ULID（比 UUID 更短、可排序）
    public static String nextId() {
        return UlidCreator.getUlid().toString();
    }
}