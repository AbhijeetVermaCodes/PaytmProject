package com.ticketbooking.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String CORRELATION_ID_KEY = "correlationId";
    public static final String USER_ID_KEY = "userId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (!StringUtils.hasText(requestId)) {
            requestId = UUID.randomUUID().toString().substring(0, 8);
        }
        response.setHeader(REQUEST_ID_HEADER, requestId);
        MDC.put(CORRELATION_ID_KEY, requestId);

        try {
            String authHeader = request.getHeader("Authorization");
            String userId = null;
            boolean isAdmin = false;

            if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7).trim();
                if (StringUtils.hasText(token)) {
                    userId = token;
                    isAdmin = "admin".equalsIgnoreCase(token) || token.startsWith("admin-");
                }
            } else {
                // Also support X-User-Id header as a convenient fallback
                String customUserHeader = request.getHeader("X-User-Id");
                if (StringUtils.hasText(customUserHeader)) {
                    userId = customUserHeader.trim();
                    isAdmin = "admin".equalsIgnoreCase(userId);
                }
            }

            if (userId != null) {
                UserContext.setUser(userId, isAdmin);
                MDC.put(USER_ID_KEY, userId);
            }

            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
            MDC.remove(CORRELATION_ID_KEY);
            MDC.remove(USER_ID_KEY);
        }
    }
}
