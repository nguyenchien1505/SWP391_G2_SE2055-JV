package com.example.SWP391_G2_SE2055_JV.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** IP / dải IP mạng chấm công — IPv4, IPv6, IPv4 viết dạng IPv6, và chuỗi không hợp lệ. */
class IpRangeTest {

    @Test
    void shouldMatchSingleIpv4() {
        IpRange range = IpRange.parse("113.160.92.202").orElseThrow();

        assertThat(range.contains("113.160.92.202")).isTrue();
        assertThat(range.contains("113.160.92.203")).isFalse();
        assertThat(range.canonical()).isEqualTo("113.160.92.202");
    }

    @Test
    void shouldMatchIpv4Range() {
        IpRange range = IpRange.parse("113.160.92.77/24").orElseThrow();

        assertThat(range.canonical()).isEqualTo("113.160.92.0/24");
        assertThat(range.contains("113.160.92.1")).isTrue();
        assertThat(range.contains("113.160.93.1")).isFalse();
    }

    /** Một số server nhận IPv4 dưới dạng IPv6 — vẫn phải khớp dải IPv4. */
    @Test
    void shouldMatchIpv4MappedAddress() {
        assertThat(IpRange.parse("10.0.0.0/8").orElseThrow().contains("::ffff:10.1.2.3")).isTrue();
    }

    @Test
    void shouldMatchIpv6Prefix() {
        IpRange range = IpRange.parse("2001:ee0:4f1:12::/64").orElseThrow();

        assertThat(range.contains("2001:ee0:4f1:12:abcd::1")).isTrue();
        assertThat(range.contains("2001:ee0:4f1:13::1")).isFalse();
        assertThat(range.contains("[2001:ee0:4f1:12::5]")).isTrue();
    }

    @Test
    void shouldNotMixIpv4AndIpv6() {
        assertThat(IpRange.parse("113.160.92.0/24").orElseThrow().contains("2001:ee0::1")).isFalse();
        assertThat(IpRange.parse("::/0").orElseThrow().contains("1.2.3.4")).isFalse();
    }

    /** Tên miền bị từ chối ngay — không bao giờ tra DNS. */
    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "localhost", "example.com", "1.2.3", "1.2.3.256", "1.2.3.4/33",
        "1.2.3.4/x", "1.2.3.4/8/8", "2001:db8::/129"})
    void shouldRejectInvalidInput(String text) {
        assertThat(IpRange.parse(text)).isEmpty();
    }

    /** IPv6 nội bộ có số card mạng ("%15") — Tomcat có thể trả về dạng này trong mạng LAN. */
    @Test
    void shouldIgnoreIpv6ZoneId() {
        IpRange range = IpRange.parse("fe80:0:0:0:1c2b:3a4d:5e6f:7a8b%15").orElseThrow();

        assertThat(range.canonical()).isEqualTo("fe80::1c2b:3a4d:5e6f:7a8b");
        assertThat(range.contains("fe80::1c2b:3a4d:5e6f:7a8b%eth0")).isTrue();
        assertThat(IpRange.parse("1.2.3.4%15")).isEmpty();
    }

    @Test
    void shouldWriteIpv6InShortForm() {
        assertThat(IpRange.parse("2001:ee0:4f1:12:0:0:0:0/64").orElseThrow().canonical()).isEqualTo("2001:ee0:4f1:12::/64");
        assertThat(IpRange.parse("::1").orElseThrow().canonical()).isEqualTo("::1");
        assertThat(IpRange.parse("2001:db8:0:1:0:0:0:1").orElseThrow().canonical()).isEqualTo("2001:db8:0:1::1");
    }

    /** «Thêm IP mạng hiện tại»: IPv4 đúng một IP; IPv6 cả dải /64 vì phần đuôi của từng máy tự đổi. */
    @Test
    void shouldSuggestNetworkForCurrentIp() {
        assertThat(IpRange.networkOf("113.160.92.202")).contains("113.160.92.202");
        assertThat(IpRange.networkOf("::ffff:113.160.92.202")).contains("113.160.92.202");
        assertThat(IpRange.networkOf("::1")).contains("::1");
        assertThat(IpRange.networkOf("abc")).isEmpty();

        String ipv6 = IpRange.networkOf("2001:ee0:4f1:12:a1b2:c3d4:e5f6:7788").orElseThrow();
        assertThat(ipv6).isEqualTo("2001:ee0:4f1:12::/64");
        // Hôm sau máy đổi phần đuôi — dải đã lưu vẫn khớp; đường mạng khác thì không.
        IpRange saved = IpRange.parse(ipv6).orElseThrow();
        assertThat(saved.contains("2001:ee0:4f1:12:9999:8888:7777:6666")).isTrue();
        assertThat(saved.contains("2001:ee0:4f1:13::1")).isFalse();
    }

    @Test
    void shouldTreatUnreadableClientIpAsOutside() {
        IpRange range = IpRange.parse("0.0.0.0/0").orElseThrow();

        assertThat(range.contains(null)).isFalse();
        assertThat(range.contains("not-an-ip")).isFalse();
    }
}
