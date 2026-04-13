package com.solv.wefin.domain.news.cluster.entity;

import com.solv.wefin.global.error.BusinessException;
import com.solv.wefin.global.error.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

/**
 * 클러스터별 AI 추천 질문
 *
 * 요약 배치 시 클러스터당 3개의 추천 질문을 사전 생성하여 저장한다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "cluster_suggested_question",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cluster_question_order",
                columnNames = {"news_cluster_id", "question_order"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClusterSuggestedQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cluster_suggested_question_id")
    private Long id;

    @Column(name = "news_cluster_id", nullable = false)
    private Long newsClusterId;

    @Column(name = "question_order", nullable = false)
    private int questionOrder;

    @Column(name = "question", nullable = false, columnDefinition = "TEXT")
    private String question;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Builder(access = AccessLevel.PRIVATE)
    private ClusterSuggestedQuestion(Long newsClusterId, int questionOrder, String question) {
        this.newsClusterId = newsClusterId;
        this.questionOrder = questionOrder;
        this.question = question;
    }

    /**
     * 추천 질문을 생성한다
     *
     * @param clusterId 클러스터 ID
     * @param order 질문 순서 (0-indexed)
     * @param question 질문 텍스트
     * @return 생성된 추천 질문 엔티티
     */
    public static ClusterSuggestedQuestion create(Long clusterId, int order, String question) {
        if (clusterId == null) {
            throw new BusinessException(ErrorCode.SUGGESTED_QUESTION_CLUSTER_ID_NULL);
        }
        if (order < 0) {
            throw new BusinessException(ErrorCode.SUGGESTED_QUESTION_ORDER_INVALID);
        }
        if (question == null || question.isBlank()) {
            throw new BusinessException(ErrorCode.SUGGESTED_QUESTION_TEXT_BLANK);
        }
        return ClusterSuggestedQuestion.builder()
                .newsClusterId(clusterId)
                .questionOrder(order)
                .question(question)
                .build();
    }
}
