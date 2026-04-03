package com.solv.wefin.global.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.global.error.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {

        String authExceptionAttr = (String) request.getAttribute("authException");

        ErrorCode errorCode = ErrorCode.AUTH_UNAUTHORIZED;

        if ("INVALID_TOKEN".equals(authExceptionAttr)) {
            errorCode = ErrorCode.AUTH_INVALID_TOKEN;
        }

        response.setStatus(errorCode.getStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiResponse<Object> body = ApiResponse.error(errorCode);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}