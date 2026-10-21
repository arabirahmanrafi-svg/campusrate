package com.campusrate.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.Clock;

/**
 * Stops spam and password guessing:
 *  - logins and sign-ups: limited per IP address, per minute
 *  - every other change (POST, PUT, DELETE): limited per user (or IP), per 10 minutes
 * Reading pages (GET) is never limited.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter writeLimiter;
    private final RateLimiter loginLimiter;

    public RateLimitInterceptor(@Value("${app.rate-limit.writes-per-10-minutes}") int writesPer10Minutes,
                                @Value("${app.rate-limit.logins-per-minute}") int loginsPerMinute) {
        Clock clock = Clock.systemUTC();
        this.writeLimiter = new RateLimiter(writesPer10Minutes, 10 * 60_000L, clock);
        this.loginLimiter = new RateLimiter(loginsPerMinute, 60_000L, clock);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String method = request.getMethod();
        if ("GET".equals(method) || "OPTIONS".equals(method) || "HEAD".equals(method)) {
            return true;
        }

        String path = request.getRequestURI();
        boolean allowed = path.startsWith("/api/auth/")
                ? loginLimiter.tryAcquire("ip:" + request.getRemoteAddr())
                : writeLimiter.tryAcquire(callerKey(request));

        if (!allowed) {
            response.setStatus(429); // Too Many Requests
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"status\":429,\"message\":\"You're doing that too often. Please wait a few minutes.\",\"fields\":{}}");
            return false;
        }
        return true;
    }

    private static String callerKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long userId) {
            return "user:" + userId;
        }
        return "ip:" + request.getRemoteAddr();
    }

    @Scheduled(fixedRate = 10 * 60_000L)
    public void cleanUp() {
        writeLimiter.cleanUp();
        loginLimiter.cleanUp();
    }
}
