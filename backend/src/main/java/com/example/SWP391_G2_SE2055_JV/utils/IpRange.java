package com.example.SWP391_G2_SE2055_JV.utils;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Một IP hoặc một dải IP dạng {@code IP/độ dài tiền tố}, IPv4 lẫn IPv6 — mạng chấm công của khách sạn (chấm
 * công M2).
 *
 * <ul>
 *   <li>Chỉ nhận IP viết bằng số: chuỗi có chữ cái ngoài hệ 16 của IPv6 bị từ chối, nên
 *       {@link InetAddress#getByName} không bao giờ phải tra DNS.</li>
 *   <li>IPv4 viết dạng IPv6 ({@code ::ffff:1.2.3.4}) được Java quy về IPv4, nên khớp được với dải IPv4.</li>
 *   <li>IPv4 không khớp IPv6 và ngược lại — nhà mạng cấp cả hai thì khai cả hai.</li>
 * </ul>
 */
public final class IpRange {

    /** IPv4 "1.2.3.4", hoặc IPv6 (chữ số hệ 16, dấu ":" và có thể kèm IPv4 ở cuối). */
    private static final Pattern IP_LITERAL = Pattern.compile("^(\\d{1,3}(\\.\\d{1,3}){3}|[0-9a-fA-F:]*:[0-9a-fA-F:.]*)$");

    private final byte[] network;
    private final int prefix;

    private IpRange(byte[] network, int prefix) {
        this.network = network;
        this.prefix = prefix;
    }

    /**
     * @param text "113.160.92.202", "113.160.92.0/24", "2001:ee0:4f1::/64"…
     * @return rỗng nếu không phải IP / dải IP hợp lệ
     */
    public static Optional<IpRange> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String[] parts = text.trim().split("/", -1);
        if (parts.length > 2) {
            return Optional.empty();
        }
        Optional<InetAddress> address = toAddress(parts[0]);
        if (address.isEmpty()) {
            return Optional.empty();
        }
        byte[] bytes = address.get().getAddress();
        int maxPrefix = bytes.length * 8;
        int prefix = maxPrefix;
        if (parts.length == 2) {
            try {
                prefix = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
            if (prefix < 0 || prefix > maxPrefix) {
                return Optional.empty();
            }
        }
        return Optional.of(new IpRange(mask(bytes, prefix), prefix));
    }

    /**
     * Mạng nên khai khi Manager bấm «Thêm IP mạng hiện tại».
     *
     * <ul>
     *   <li>IPv4: đúng một IP — cả khách sạn ra Internet chung một IP công khai (NAT).</li>
     *   <li>IPv6: cả dải {@code /64}. Nhà mạng cấp cố định 64 bit đầu cho đường mạng; 64 bit sau do từng máy tự
     *       đặt và đổi định kỳ (privacy address), lưu đúng một địa chỉ thì vài giờ sau đã không khớp nữa.</li>
     * </ul>
     *
     * @return rỗng nếu {@code ip} không đọc được
     */
    public static Optional<String> networkOf(String ip) {
        return toAddress(ip).map(address -> {
            byte[] bytes = address.getAddress();
            // ::1 khi chạy thử trên chính máy server — giữ đúng một địa chỉ.
            int prefix = bytes.length == 16 && !address.isLoopbackAddress() ? 64 : bytes.length * 8;
            return new IpRange(mask(bytes, prefix), prefix).canonical();
        });
    }

    /** IP của client có nằm trong dải này không. IP không đọc được thì coi như không khớp. */
    public boolean contains(String ip) {
        return toAddress(ip)
            .map(InetAddress::getAddress)
            .filter(bytes -> bytes.length == network.length)
            .map(bytes -> Arrays.equals(mask(bytes, prefix), network))
            .orElse(false);
    }

    /** Dạng chuẩn để lưu và so trùng: địa chỉ mạng đã cắt theo tiền tố, bỏ "/32" ("/128") khi là một IP. */
    public String canonical() {
        String base;
        try {
            base = InetAddress.getByAddress(network).getHostAddress();
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);   // không xảy ra: mảng 4 / 16 byte luôn hợp lệ
        }
        if (network.length == 16) {
            base = compressIpv6(base);
        }
        return prefix == network.length * 8 ? base : base + "/" + prefix;
    }

    /**
     * Java viết IPv6 đủ 8 nhóm ("2001:ee0:4f1:12:0:0:0:0"); rút chuỗi nhóm 0 dài nhất thành "::" như cách mọi người
     * vẫn viết (RFC 5952) — "2001:ee0:4f1:12::".
     */
    private static String compressIpv6(String full) {
        String[] groups = full.split(":");
        int bestStart = -1, bestLength = 0;
        for (int i = 0; i < groups.length; ) {
            if (!groups[i].equals("0")) {
                i++;
                continue;
            }
            int start = i;
            while (i < groups.length && groups[i].equals("0")) {
                i++;
            }
            if (i - start > bestLength) {
                bestStart = start;
                bestLength = i - start;
            }
        }
        if (bestLength < 2) {
            return full;
        }
        String head = String.join(":", Arrays.copyOfRange(groups, 0, bestStart));
        String tail = String.join(":", Arrays.copyOfRange(groups, bestStart + bestLength, groups.length));
        return head + "::" + tail;
    }

    private static Optional<InetAddress> toAddress(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String trimmed = text.trim();
        // "[::1]" — dạng IPv6 có ngoặc vuông một số proxy gửi kèm.
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        // "fe80::1%15" — IPv6 nội bộ kèm số card mạng. Số đó chỉ có nghĩa trên máy nhận, không thuộc địa chỉ.
        int zone = trimmed.indexOf('%');
        if (zone >= 0 && trimmed.contains(":")) {
            trimmed = trimmed.substring(0, zone);
        }
        if (!IP_LITERAL.matcher(trimmed).matches()) {
            return Optional.empty();
        }
        try {
            InetAddress address = InetAddress.getByName(trimmed);
            if (address instanceof Inet4Address && trimmed.contains(".") && !trimmed.contains(":")) {
                // Java nhận "1.2.3" hay số > 255 theo kiểu cũ — tự kiểm từng phần cho chắc.
                for (String part : trimmed.split("\\.")) {
                    if (Integer.parseInt(part) > 255) {
                        return Optional.empty();
                    }
                }
            }
            return Optional.of(address);
        } catch (UnknownHostException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static byte[] mask(byte[] bytes, int prefix) {
        byte[] result = bytes.clone();
        for (int i = 0; i < result.length; i++) {
            int bitsLeft = prefix - i * 8;
            if (bitsLeft >= 8) {
                continue;
            }
            result[i] = bitsLeft <= 0 ? 0 : (byte) (result[i] & (0xFF << (8 - bitsLeft)));
        }
        return result;
    }
}
