package com.xtu.homework.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xtu.homework.dao.VerificationCodeDao;
import com.xtu.homework.entity.VerificationCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * 邮箱注册验证码服务
 * - 6 位数字验证码，SHA-256 哈希存储（不落明文）
 * - 5 分钟有效，60 秒冷却，单邮箱每日最多 10 条
 */
@Service
@RequiredArgsConstructor
public class VerificationCodeService {

    private final VerificationCodeDao dao;

    private static final int TTL_SECONDS = 300;        // 5 分钟有效
    private static final int COOLDOWN_SECONDS = 60;    // 60 秒冷却
    private static final int DAILY_LIMIT = 10;         // 单邮箱日限
    private final SecureRandom secureRandom = new SecureRandom();

    /** 生成并发送验证码，返回明文（调用方负责发送邮件） */
    public String issue(String channel, String target) {
        String normalizedChannel = normalizeChannel(channel);
        String normalizedTarget = normalizeTarget(target);
        LocalDateTime now = LocalDateTime.now();

        // 冷却校验：取最近一条
        List<VerificationCode> recent = dao.selectList(new LambdaQueryWrapper<VerificationCode>()
                .eq(VerificationCode::getChannel, normalizedChannel)
                .eq(VerificationCode::getTarget, normalizedTarget)
                .orderByDesc(VerificationCode::getCreatedAt)
                .last("LIMIT 1"));
        if (!recent.isEmpty()) {
            VerificationCode last = recent.get(0);
            LocalDateTime nextAllowed = last.getLastSentAt().plusSeconds(COOLDOWN_SECONDS);
            if (now.isBefore(nextAllowed)) {
                long wait = java.time.Duration.between(now, nextAllowed).getSeconds();
                throw new RuntimeException("发送过于频繁，请 " + wait + " 秒后重试");
            }
        }

        // 当日次数校验
        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
        Long sentToday = dao.selectCount(new LambdaQueryWrapper<VerificationCode>()
                .eq(VerificationCode::getChannel, normalizedChannel)
                .eq(VerificationCode::getTarget, normalizedTarget)
                .ge(VerificationCode::getCreatedAt, startOfDay));
        if (sentToday != null && sentToday >= DAILY_LIMIT) {
            throw new RuntimeException("该邮箱今日验证码发送次数已达上限（" + DAILY_LIMIT + " 次）");
        }

        String plainCode = generateSixDigitCode();
        VerificationCode vc = new VerificationCode();
        vc.setChannel(normalizedChannel);
        vc.setTarget(normalizedTarget);
        vc.setCodeHash(hash(normalizedChannel, normalizedTarget, plainCode));
        vc.setExpiresAt(now.plusSeconds(TTL_SECONDS));
        vc.setLastSentAt(now);
        vc.setSendCountToday(sentToday == null ? 1 : sentToday.intValue() + 1);
        vc.setCreatedAt(now);
        dao.insert(vc);
        return plainCode;
    }

    /** 校验验证码，成功后标记已用 */
    public void verify(String channel, String target, String code) {
        String normalizedChannel = normalizeChannel(channel);
        String normalizedTarget = normalizeTarget(target);
        if (code == null || !code.trim().matches("\\d{6}")) {
            throw new RuntimeException("验证码格式错误");
        }
        LocalDateTime now = LocalDateTime.now();
        List<VerificationCode> list = dao.selectList(new LambdaQueryWrapper<VerificationCode>()
                .eq(VerificationCode::getChannel, normalizedChannel)
                .eq(VerificationCode::getTarget, normalizedTarget)
                .isNull(VerificationCode::getUsedAt)
                .gt(VerificationCode::getExpiresAt, now)
                .orderByDesc(VerificationCode::getCreatedAt)
                .last("LIMIT 1"));
        if (list.isEmpty()) {
            throw new RuntimeException("验证码无效或已过期，请重新获取");
        }
        VerificationCode vc = list.get(0);
        String expected = hash(normalizedChannel, normalizedTarget, code.trim());
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                vc.getCodeHash().getBytes(StandardCharsets.UTF_8))) {
            throw new RuntimeException("验证码错误");
        }
        vc.setUsedAt(now);
        dao.updateById(vc);
    }

    private String normalizeChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            throw new RuntimeException("验证码渠道不能为空");
        }
        String c = channel.trim().toLowerCase(Locale.ROOT);
        if (!"email".equals(c)) {
            throw new RuntimeException("不支持的验证码渠道");
        }
        return c;
    }

    private String normalizeTarget(String target) {
        if (target == null || target.isBlank()) {
            throw new RuntimeException("邮箱不能为空");
        }
        String t = target.trim();
        if (!t.matches("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$")) {
            throw new RuntimeException("邮箱格式不正确");
        }
        return t;
    }

    private String generateSixDigitCode() {
        return String.format(Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000));
    }

    private String hash(String channel, String target, String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((channel + ":" + target + ":" + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
