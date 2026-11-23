package com.groupcc2.recruitment.common.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TrustedProxyAddressResolver {
    private final List<Network> trustedNetworks = new ArrayList<>();

    public TrustedProxyAddressResolver(String trustedProxies) {
        if (trustedProxies == null) return;
        for (String entry : trustedProxies.split(",")) {
            addNetwork(entry.trim());
        }
    }

    public String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (!isTrusted(remote)) return normalizedAddress(remote);

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            for (int i = hops.length - 1; i >= 0; i--) {
                String hop = hops[i].trim();
                if (hop.isBlank()) continue;
                if (isTrusted(hop)) continue;
                return normalizedAddress(hop);
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        return normalizedAddress(realIp == null || realIp.isBlank() ? remote : realIp.trim());
    }

    boolean isTrusted(String address) {
        try {
            InetAddress candidate = InetAddress.getByName(address);
            for (Network network : trustedNetworks) {
                if (network.contains(candidate)) return true;
            }
            return false;
        } catch (UnknownHostException | RuntimeException exception) {
            return false;
        }
    }

    private void addNetwork(String entry) {
        if (entry.isBlank()) return;
        try {
            String[] parts = entry.split("/", 2);
            InetAddress address = InetAddress.getByName(parts[0]);
            int prefix = parts.length == 2 ? Integer.parseInt(parts[1]) : address.getAddress().length * 8;
            trustedNetworks.add(new Network(address.getAddress(), prefix));
        } catch (Exception ignored) {
            // Invalid proxy entries are ignored so no untrusted forwarding header is accepted.
        }
    }

    private static String normalizedAddress(String address) {
        if (address == null || address.isBlank()) return "unknown";
        try {
            return InetAddress.getByName(address).getHostAddress();
        } catch (UnknownHostException exception) {
            return "unknown";
        }
    }

    private record Network(byte[] address, int prefix) {
        boolean contains(InetAddress candidate) {
            byte[] bytes = candidate.getAddress();
            if (bytes.length != address.length) return false;
            int fullBytes = prefix / 8;
            int remainingBits = prefix % 8;
            if (!Arrays.equals(
                    java.util.Arrays.copyOf(address, fullBytes),
                    java.util.Arrays.copyOf(bytes, fullBytes))) return false;
            if (remainingBits == 0) return true;
            int mask = 0xFF << (8 - remainingBits);
            return (address[fullBytes] & mask) == (bytes[fullBytes] & mask);
        }
    }
}
