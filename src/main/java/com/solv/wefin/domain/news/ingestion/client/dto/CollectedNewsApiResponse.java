package com.solv.wefin.domain.news.ingestion.client.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@Builder
public class CollectedNewsApiResponse {

    private String externalArticleId;
    private String originalUrl;
    private String originalTitle;
    private String originalContent;
    private String originalThumbnailUrl;
    private OffsetDateTime originalPublishedAt;
    private String publisherName;
    private String rawPayload;
}
