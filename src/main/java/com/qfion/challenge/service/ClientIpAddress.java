package com.qfion.challenge.service;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;

/** Numeric addresses only: never perform DNS for client-controlled input. */
public final class ClientIpAddress {
    private ClientIpAddress() {}
    public static InetAddress parse(String value) {
        if (value == null || value.length() > 45) return null;
        if (!(value.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}") ||
                (value.contains(":") && value.matches("[0-9a-fA-F:.]+")))) return null;
        try { return InetAddress.getByName(value); }
        catch (UnknownHostException error) { return null; }
    }

    public static String from(HttpServletRequest request, int trustedProxyHops) {
        String value = request.getRemoteAddr();
        if (trustedProxyHops > 0) {
            // Enable only when direct access is restricted to the configured proxy chain.
            String header = request.getHeader("X-Forwarded-For");
            if (trustedProxyHops > 8 || header == null || header.length() > 2048) return null;
            String[] chain = header.split(",", -1);
            if (chain.length < trustedProxyHops) return null;
            value = chain[chain.length - trustedProxyHops].trim();
        }
        InetAddress address = parse(value);
        return address == null ? null : address.getHostAddress();
    }

    public static boolean isPublic(InetAddress address) {
        if (address == null || address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        int a = bytes[0] & 255, b = bytes[1] & 255;
        if (bytes.length == 16) {
            // IPv6 global unicast, excluding documentation allocations.
            return (a & 224) == 32 && !(a == 32 && b == 1 && (bytes[2] & 255) == 13 && (bytes[3] & 255) == 184)
                    && !(a == 63 && (b & 240) == 240);
        }
        int c = bytes[2] & 255;
        return a != 0 && a < 224 && !(a == 100 && b >= 64 && b <= 127)
                && !(a == 192 && b == 0 && (c == 0 || c == 2))
                && !(a == 198 && (b == 18 || b == 19 || (b == 51 && c == 100)))
                && !(a == 203 && b == 0 && c == 113);
    }
}
