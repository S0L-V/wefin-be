package com.solv.wefin.domain.game.vote;

import java.util.UUID;

/**
 * 투표 이벤트를 클라이언트에게 브로드캐스트하는 인터페이스.
 * domain 계층에 인터페이스를 두고, web 계층에서 WebSocket으로 구현한다.
 * (domain -> web 의존 방지)
 */
public interface VoteBroadcaster {

    void broadcastStart(UUID roomId, String initiatorNickname, int totalCount, int timeoutSeconds);

    void broadcastUpdate(UUID roomId, long agreeCount, long disagreeCount, int totalCount);

    void broadcastResult(UUID roomId, boolean passed, long agreeCount, long disagreeCount);
}
