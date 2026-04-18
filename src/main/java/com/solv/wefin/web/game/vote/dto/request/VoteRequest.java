package com.solv.wefin.web.game.vote.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class VoteRequest {

    @NotNull(message = "찬성/반대 여부는 필수입니다")
    private Boolean agree;
}
