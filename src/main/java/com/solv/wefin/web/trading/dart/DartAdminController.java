package com.solv.wefin.web.trading.dart;

import com.solv.wefin.domain.trading.dart.service.DartCorpCodeLoader;
import com.solv.wefin.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile({"local", "dev", "desktop"})
@RestController
@RequestMapping("/api/admin/dart")
@RequiredArgsConstructor
public class DartAdminController {

    private final DartCorpCodeLoader dartCorpCodeLoader;

    @PostMapping("/corp-codes/refresh")
    public ApiResponse<String> refreshCorpCodes() {
        int count = dartCorpCodeLoader.refresh();
        return ApiResponse.success(String.format("DART corpCode 갱신 완료: %d건", count));
    }
}
