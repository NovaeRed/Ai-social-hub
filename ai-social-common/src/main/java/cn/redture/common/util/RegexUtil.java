package cn.redture.common.util;

import java.util.regex.Pattern;

/**
 * 正则表达式工具类
 */
public class RegexUtil {

    /**
     * 电子邮箱格式正则表达式
     */
    private static final String EMAIL_REGEX = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}$";

    /**
     * 中国大陆手机号码格式正则表达式
     */
    private static final String PHONE_REGEX = "^1[3-9]\\\\d{9}$";

    private static final Pattern EMAIL_PATTERN = Pattern.compile(EMAIL_REGEX);
    private static final Pattern PHONE_PATTERN = Pattern.compile(PHONE_REGEX);

    /**
     * 校验字符串是否为合法的电子邮箱格式
     *
     * @param email 待校验的字符串
     * @return 如果是合法的邮箱格式则返回 true，否则返回 false
     */
    public static boolean isEmail(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email).matches();
    }

    /**
     * 校验字符串是否为合法的中国大陆手机号码格式
     *
     * @param phone 待校验的字符串
     * @return 如果是合法的手机号码格式则返回 true，否则返回 false
     */
    public static boolean isPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return false;
        }
        return PHONE_PATTERN.matcher(phone).matches();
    }
}
