package com.solv.wefin.web.user;

import com.solv.wefin.domain.user.dto.MyPageInfo;
import com.solv.wefin.domain.user.service.UserService;
import com.solv.wefin.global.common.ApiResponse;
import com.solv.wefin.web.user.dto.MyPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ApiResponse<MyPageResponse> getMyPage(@AuthenticationPrincipal UUID userId) {
        MyPageInfo info = userService.getMyPage(userId);
        return ApiResponse.success(MyPageResponse.from(info));
    }
}