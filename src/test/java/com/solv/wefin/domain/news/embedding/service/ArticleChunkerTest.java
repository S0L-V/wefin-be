package com.solv.wefin.domain.news.embedding.service;

import com.solv.wefin.domain.news.embedding.chunk.ArticleChunker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArticleChunkerTest {

    private ArticleChunker articleChunker;

    @BeforeEach
    void setUp() {
        articleChunker = new ArticleChunker();
    }

    @Test
    @DisplayName("짧은 기사는 청크 1개로 반환된다")
    void shortArticle_singleChunk() {
        // given
        String title = "테스트 제목";
        String content = "짧은 본문입니다.";

        // when
        List<ArticleChunker.Chunk> chunks = articleChunker.chunk(title, content);

        // then
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getChunkIndex()).isZero();
        assertThat(chunks.get(0).getChunkText()).contains("테스트 제목");
        assertThat(chunks.get(0).getChunkText()).contains("짧은 본문입니다.");
        assertThat(chunks.get(0).getTokenCount()).isGreaterThan(0);
    }

    @Test
    @DisplayName("긴 기사는 여러 청크로 분할된다")
    void longArticle_multipleChunks() {
        // given
        String title = "긴 기사 제목";
        StringBuilder contentBuilder = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            contentBuilder.append("이것은 테스트 문장 번호 ").append(i).append("입니다. ");
        }
        String content = contentBuilder.toString();

        // when
        List<ArticleChunker.Chunk> chunks = articleChunker.chunk(title, content);

        // then
        assertThat(chunks.size()).isGreaterThan(1);
        assertThat(chunks.get(0).getChunkIndex()).isZero();
        assertThat(chunks.get(0).getChunkText()).contains("긴 기사 제목");

        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).getChunkIndex()).isEqualTo(i);
            assertThat(chunks.get(i).getChunkText()).isNotBlank();
            assertThat(chunks.get(i).getTokenCount()).isGreaterThan(0);
        }
    }

    @Test
    @DisplayName("청크 인덱스는 0부터 연속적으로 증가한다")
    void chunkIndex_sequential() {
        // given
        StringBuilder contentBuilder = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            contentBuilder.append("반복 문장입니다. ");
        }

        // when
        List<ArticleChunker.Chunk> chunks = articleChunker.chunk("제목", contentBuilder.toString());

        // then
        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).getChunkIndex()).isEqualTo(i);
        }
    }

    @Test
    @DisplayName("title이 null이면 content만으로 청크를 생성한다")
    void nullTitle_contentOnly() {
        // given
        String content = "본문만 있는 기사입니다.";

        // when
        List<ArticleChunker.Chunk> chunks = articleChunker.chunk(null, content);

        // then
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getChunkText()).isEqualTo("본문만 있는 기사입니다.");
    }

    @Test
    @DisplayName("content가 null이면 title만으로 청크를 생성한다")
    void nullContent_titleOnly() {
        // given
        String title = "제목만 있는 기사";

        // when
        List<ArticleChunker.Chunk> chunks = articleChunker.chunk(title, null);

        // then
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getChunkText()).isEqualTo("제목만 있는 기사");
    }

    @Test
    @DisplayName("title과 content가 모두 빈 문자열이면 빈 리스트를 반환한다")
    void emptyTitleAndContent_emptyList() {
        // when
        List<ArticleChunker.Chunk> chunks = articleChunker.chunk("", "");

        // then
        assertThat(chunks).isEmpty();
    }

    @Test
    @DisplayName("한글 텍스트의 토큰 수를 근사 추정한다")
    void estimateTokenCount_korean() {
        // given — 한글 10자 → 약 5토큰
        String korean = "가나다라마바사아자차";

        // when
        int tokens = articleChunker.estimateTokenCount(korean);

        // then
        assertThat(tokens).isEqualTo(5);
    }

    @Test
    @DisplayName("영문 텍스트의 토큰 수를 근사 추정한다")
    void estimateTokenCount_english() {
        // given — 영문 12자 → 약 3토큰
        String english = "abcdefghijkl";

        // when
        int tokens = articleChunker.estimateTokenCount(english);

        // then
        assertThat(tokens).isEqualTo(3);
    }

    @Test
    @DisplayName("빈 문자열의 토큰 수는 0이다")
    void estimateTokenCount_empty() {
        assertThat(articleChunker.estimateTokenCount("")).isZero();
        assertThat(articleChunker.estimateTokenCount(null)).isZero();
    }
}
