package me.zhengziheng.agent.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 每 IP 限流（内存滑动窗口，单实例）。
 * 两档：
 *  - 问答/反馈：默认 50 次/小时（按"面试官深度追问 + 同公司 NAT 共用出口 IP"留足余量）；
 *  - 登录：默认 10 次/小时（公开无验证码，防暴力破解站长密码）。
 * 实现：key=IP 的时间戳队列，窗口内超过上限返回 false；惰性清理过期时间戳防内存增长。
 */
@Service
public class RateLimitService {

    private final int questionPerHour;
    private final int loginPerHour;

    /** 问答/反馈计数窗口 */
    private final Map<String, Deque<Long>> questionWindows = new ConcurrentHashMap<>();
    /** 登录计数窗口 */
    private final Map<String, Deque<Long>> loginWindows = new ConcurrentHashMap<>();

    public RateLimitService(@Value("${rag.rate-limit.per-hour:50}") int questionPerHour,
                            @Value("${rag.rate-limit.login-per-hour:10}") int loginPerHour) {
        this.questionPerHour = questionPerHour;
        this.loginPerHour = loginPerHour;
    }

    /** 问答/反馈是否放行 */
    public boolean tryAcquireQuestion(String ip) {
        return tryAcquire(questionWindows, ip, questionPerHour);
    }

    /** 登录是否放行 */
    public boolean tryAcquireLogin(String ip) {
        return tryAcquire(loginWindows, ip, loginPerHour);
    }

    private boolean tryAcquire(Map<String, Deque<Long>> windows, String ip, int limit) {
        if (ip == null || ip.isBlank()) {
            ip = "unknown";
        }
        long now = System.currentTimeMillis();
        long windowMs = 60 * 60 * 1000L;
        Deque<Long> q = windows.computeIfAbsent(ip, k -> new ConcurrentLinkedDeque<>());
        synchronized (q) {
            // 惰性清理：弹出窗口外的时间戳
            while (!q.isEmpty() && q.peekFirst() != null && now - q.peekFirst() > windowMs) {
                q.pollFirst();
            }
            if (q.size() >= limit) {
                return false;
            }
            q.addLast(now);
            return true;
        }
    }

    /** 清理所有过期窗口（定时任务调用） */
    public void evictExpired() {
        long now = System.currentTimeMillis();
        long windowMs = 60 * 60 * 1000L;
        for (Deque<Long> q : questionWindows.values()) {
            synchronized (q) {
                while (!q.isEmpty() && q.peekFirst() != null && now - q.peekFirst() > windowMs) {
                    q.pollFirst();
                }
            }
        }
        for (Deque<Long> q : loginWindows.values()) {
            synchronized (q) {
                while (!q.isEmpty() && q.peekFirst() != null && now - q.peekFirst() > windowMs) {
                    q.pollFirst();
                }
            }
        }
    }
}
