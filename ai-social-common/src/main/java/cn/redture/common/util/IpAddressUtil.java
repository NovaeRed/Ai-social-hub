package cn.redture.common.util;

import jakarta.servlet.http.HttpServletRequest;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * IP 地址提取与标准化工具。
 */
public final class IpAddressUtil {

    private static final String UNKNOWN = "unknown";

    private IpAddressUtil() {
    }

    public static String extractClientIp(HttpServletRequest request) {
        return extractClientIp(request, Collections.emptyList());
    }

    public static String extractClientIp(HttpServletRequest request, List<String> trustedProxies) {
        if (request == null) {
            return "127.0.0.1";
        }

        String remoteAddr = normalizeIp(request.getRemoteAddr());
        List<String> trustRules = trustedProxies == null ? List.of() : trustedProxies;

        List<String> forwardedChain = ipChainFromForwarded(request.getHeader("Forwarded"));
        if (!forwardedChain.isEmpty()) {
            return extractByTrustedProxyChain(remoteAddr, forwardedChain, trustRules);
        }

        List<String> xffChain = ipChainFromXForwardedFor(request.getHeader("X-Forwarded-For"));
        if (!xffChain.isEmpty()) {
            return extractByTrustedProxyChain(remoteAddr, xffChain, trustRules);
        }

        String realIp = request.getHeader("X-Real-IP");
        if (isUsable(realIp)) {
            return normalizeIp(realIp);
        }

        String proxyClientIp = request.getHeader("Proxy-Client-IP");
        if (isUsable(proxyClientIp)) {
            return normalizeIp(proxyClientIp);
        }

        String wlProxyClientIp = request.getHeader("WL-Proxy-Client-IP");
        if (isUsable(wlProxyClientIp)) {
            return normalizeIp(wlProxyClientIp);
        }

        return remoteAddr;
    }

    public static String normalizeIp(String rawIp) {
        if (!isUsable(rawIp)) {
            return "127.0.0.1";
        }

        String candidate = stripPortAndBrackets(rawIp.trim());
        if ("localhost".equalsIgnoreCase(candidate)) {
            return "127.0.0.1";
        }

        try {
            InetAddress address = InetAddress.getByName(candidate);

            if (address.isLoopbackAddress()) {
                return "127.0.0.1";
            }

            if (address instanceof Inet6Address inet6Address) {
                byte[] bytes = inet6Address.getAddress();
                if (isIpv4MappedIpv6(bytes)) {
                    return toIpv4Dotted(bytes);
                }
            }

            return address.getHostAddress();
        } catch (Exception ex) {
            return candidate;
        }
    }

    private static String extractByTrustedProxyChain(String remoteAddr, List<String> chain, List<String> trustedProxies) {
        if (!isTrustedProxy(remoteAddr, trustedProxies)) {
            return remoteAddr;
        }

        String candidate = remoteAddr;
        for (int i = chain.size() - 1; i >= 0; i--) {
            String priorHop = chain.get(i);
            if (!isTrustedProxy(candidate, trustedProxies)) {
                break;
            }
            candidate = priorHop;
        }
        return candidate;
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
                    if (isUsable(value)) {
                        chain.add(normalizeIp(value));
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
            String candidate = part == null ? null : part.trim();
            if (isUsable(candidate)) {
                chain.add(normalizeIp(candidate));
            }
        }

        return chain;
    }

    private static String stripPortAndBrackets(String value) {
        String candidate = value;

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

            int mask = (~((1 << (8 - remainingBits)) - 1)) & 0xFF;
            return (subnetBytes[fullBytes] & mask) == (targetBytes[fullBytes] & mask);
        } catch (Exception ex) {
            return false;
        }
    }
}
