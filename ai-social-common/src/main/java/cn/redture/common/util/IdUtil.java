package cn.redture.common.util;

import com.github.f4b6a3.ulid.UlidCreator;

public class IdUtil {
    public static String nextId() {
        return UlidCreator.getUlid().toString();
    }
}