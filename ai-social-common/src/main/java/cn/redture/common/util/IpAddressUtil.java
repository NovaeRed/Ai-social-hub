package cn.redture.common.util;

import jakarta.servlet.http.HttpServletRequest;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * IP 地址提取与标准化工具。
 */
public final class IpAddressUtil {

    private static final String UNKNOWN = "unknown";
    private static final String LOOPBACK_IPV4 = "127.0.0.1";
    private static final String LOCALHOST = "localhost";

    private IpAddressUtil() {
    }

    public static String extractClientIp(HttpServletRequest request, List<String> trustedProxies) {
        if (request == null) {
            return LOOPBACK_IPV4;
        }

        String remoteAddr = normalizeIp(request.getRemoteAddr());
        if (remoteAddr == null) {
            return LOOPBACK_IPV4;
        }

        List<String> trustRules = trustedProxies == null ? List.of() : trustedProxies;
        if (trustRules.isEmpty()) {
            return remoteAddr;
        }

        // 安全边界：直连来源不在受信列表中时，不信任何代理头。
        if (!isTrustedProxy(remoteAddr, trustRules)) {
            return remoteAddr;
        }

        // 只有直连IP在受信列表中时，才处理代理头
        List<String> forwardedChain = ipChainFromForwarded(request.getHeader("Forwarded"));
        if (!forwardedChain.isEmpty()) {
            return extractClientFromProxyChain(remoteAddr, forwardedChain, trustRules);
        }

        List<String> xffChain = ipChainFromXForwardedFor(request.getHeader("X-Forwarded-For"));
        if (!xffChain.isEmpty()) {
            return extractClientFromProxyChain(remoteAddr, xffChain, trustRules);
        }

        String realIp = request.getHeader("X-Real-IP");
        String normalizedRealIp = normalizeIp(realIp);
        if (normalizedRealIp != null) {
            return normalizedRealIp;
        }

        String proxyClientIp = request.getHeader("Proxy-Client-IP");
        String normalizedProxyClientIp = normalizeIp(proxyClientIp);
        if (normalizedProxyClientIp != null) {
            return normalizedProxyClientIp;
        }

        String wlProxyClientIp = request.getHeader("WL-Proxy-Client-IP");
        String normalizedWlProxyClientIp = normalizeIp(wlProxyClientIp);
        if (normalizedWlProxyClientIp != null) {
            return normalizedWlProxyClientIp;
        }

        return remoteAddr;
    }

    public static String normalizeIp(String rawIp) {
        if (!isUsable(rawIp)) {
            return null;
        }

        String candidate = stripPortAndBrackets(rawIp.trim());
        if (!isUsable(candidate)) {
            return null;
        }

        if (LOCALHOST.equalsIgnoreCase(candidate)) {
            return LOOPBACK_IPV4;
        }

        if (containsInvalidIpChars(candidate)) {
            return null;
        }

        try {
            InetAddress address = InetAddress.getByName(candidate);

            if (address.isLoopbackAddress()) {
                return LOOPBACK_IPV4;
            }

            if (address instanceof Inet6Address inet6Address) {
                byte[] bytes = inet6Address.getAddress();
                if (isIpv4MappedIpv6(bytes)) {
                    return toIpv4Dotted(bytes);
                }
            }

            return address.getHostAddress();
        } catch (Exception ex) {
            return null;
        }
    }

    private static String extractClientFromProxyChain(String remoteAddr, List<String> chain, List<String> trustedProxies) {
        // 代理链应从右到左回溯，找到第一个非可信代理地址作为真实客户端。
        String currentHop = remoteAddr;
        for (int i = chain.size() - 1; i >= 0; i--) {
            if (!isTrustedProxy(currentHop, trustedProxies)) {
                return currentHop;
            }

            String previousHop = chain.get(i);
            if (previousHop == null) {
                continue;
            }
            currentHop = previousHop;
        }

        // 如果整条链都在受信范围，保守返回链首（最原始一跳）。
        return currentHop;
    }

    private static List<String> ipChainFromForwarded(String forwardedHeader) {
        if (!isUsable(forwardedHeader)) {
            return List.of();
        }

        List<String> chain = new ArrayList<>();
        String[] elements = forwardedHeader.split(",");
        for (String element : elements) {
            String[] pairs = element.split(";");
            for (String pair : pairs) {
                String trimmedPair = pair.trim();
                if (trimmedPair.regionMatches(true, 0, "for=", 0, 4)) {
                    String value = trimmedPair.substring(4).trim();
                    if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                        value = value.substring(1, value.length() - 1);
                    }
                    String normalizedIp = normalizeIp(value);
                    if (normalizedIp != null) {
                        chain.add(normalizedIp);
                    }
                }
            }
        }

        return chain;
    }

    private static List<String> ipChainFromXForwardedFor(String xffHeader) {
        if (!isUsable(xffHeader)) {
            return List.of();
        }

        List<String> chain = new ArrayList<>();
        String[] parts = xffHeader.split(",");
        for (String part : parts) {
            String normalizedIp = normalizeIp(part);
            if (normalizedIp != null) {
                chain.add(normalizedIp);
            }
        }

        return chain;
    }

    private static String stripPortAndBrackets(String value) {
        String candidate = value;

        if (candidate.startsWith("\"") && candidate.endsWith("\"") && candidate.length() >= 2) {
            candidate = candidate.substring(1, candidate.length() - 1);
        }

        if (candidate.startsWith("[") && candidate.contains("]")) {
            int end = candidate.indexOf(']');
            if (end > 1) {
                return candidate.substring(1, end);
            }
        }

        int firstColon = candidate.indexOf(':');
        int lastColon = candidate.lastIndexOf(':');
        if (firstColon > 0 && firstColon == lastColon && candidate.contains(".")) {
            return candidate.substring(0, firstColon);
        }

        return candidate;
    }

    private static boolean containsInvalidIpChars(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean allowed = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F')
                    || c == '.'
                    || c == ':'
                    || c == '%';
            if (!allowed) {
                return true;
            }
        }
        return false;
    }

    private static boolean isIpv4MappedIpv6(byte[] bytes) {
        if (bytes == null || bytes.length != 16) {
            return false;
        }

        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }

        return (bytes[10] & 0xFF) == 0xFF && (bytes[11] & 0xFF) == 0xFF;
    }

    private static String toIpv4Dotted(byte[] bytes) {
        return (bytes[12] & 0xFF) + "."
                + (bytes[13] & 0xFF) + "."
                + (bytes[14] & 0xFF) + "."
                + (bytes[15] & 0xFF);
    }

    private static boolean isUsable(String value) {
        return value != null && !value.isBlank() && !UNKNOWN.equalsIgnoreCase(value.trim());
    }

    private static boolean isTrustedProxy(String ip, List<String> trustedProxies) {
        if (!isUsable(ip) || trustedProxies == null || trustedProxies.isEmpty()) {
            return false;
        }

        for (String rule : trustedProxies) {
            if (matchesRule(ip, rule)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesRule(String ip, String rule) {
        if (!isUsable(rule)) {
            return false;
        }

        String normalizedIp = normalizeIp(ip);
        if (normalizedIp == null) {
            return false;
        }
        String trimmedRule = rule.trim();

        if (!trimmedRule.contains("/")) {
            return normalizedIp.equals(normalizeIp(trimmedRule));
        }

        try {
            String[] parts = trimmedRule.split("/", 2);
            InetAddress subnet = InetAddress.getByName(stripPortAndBrackets(parts[0].trim()));
            InetAddress target = InetAddress.getByName(stripPortAndBrackets(normalizedIp));

            int prefix = Integer.parseInt(parts[1].trim());
            byte[] subnetBytes = subnet.getAddress();
            byte[] targetBytes = target.getAddress();
            if (subnetBytes.length != targetBytes.length) {
                return false;
            }

            int maxBits = subnetBytes.length * 8;
            if (prefix < 0 || prefix > maxBits) {
                return false;
            }

            int fullBytes = prefix / 8;
            int remainingBits = prefix % 8;

            for (int i = 0; i < fullBytes; i++) {
                if (subnetBytes[i] != targetBytes[i]) {
                    return false;
                }
            }

            if (remainingBits == 0) {
                return true;
            }

            int mask = (-(1 << (8 - remainingBits))) & 0xFF;
            return (subnetBytes[fullBytes] & mask) == (targetBytes[fullBytes] & mask);
        } catch (Exception ex) {
            return false;
        }
    }
}