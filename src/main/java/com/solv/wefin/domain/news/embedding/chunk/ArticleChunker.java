package com.solv.wefin.domain.news.embedding.chunk;

import lombok.Builder;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 기사 본문을 토큰 기준으로 청크 분할한다.
 * RAG 근거 문단 추적을 위해 기사 전체를 하나의 벡터로 만들지 않고,
 * 약 500토큰 단위로 문장 경계에서 나누어 청크별 임베딩을 생성할 수 있도록 한다.
 * 첫 번째 청크에는 제목을 포함시켜 기사의 맥락을 유지한다.
 */
@Component
public class ArticleChunker {

    private static final int TARGET_CHUNK_TOKENS = 500;
    private static final double KOREAN_CHARS_PER_TOKEN = 2.0;
    private static final double NON_KOREAN_CHARS_PER_TOKEN = 4.0;

    /**
     * 제목과 본문을 결합하여 토큰 기준 청크로 분할한다.
     *
     * @param title   기사 제목
     * @param content 기사 본문
     * @return 분할된 청크 목록 (최소 1개)
     */
    public List<Chunk> chunk(String title, String content) {
        String fullText = buildFullText(title, content);

        if (fullText.isBlank()) {
            return List.of();
        }

        int estimatedTokens = estimateTokenCount(fullText);
        if (estimatedTokens <= TARGET_CHUNK_TOKENS) {
            return List.of(createChunk(0, fullText, estimatedTokens));
        }

        return splitBySentenceBoundary(fullText);
    }

    /**
     * 제목과 본문을 하나의 텍스트로 결합한다.
     * 제목이 있으면 본문 앞에 줄바꿈 2개로 구분하여 붙인다.
     */
    private String buildFullText(String title, String content) {
        String normalizedTitle = normalize(title);
        String normalizedContent = normalize(content);

        if (normalizedTitle.isEmpty()) {
            return normalizedContent;
        }
        if (normalizedContent.isEmpty()) {
            return normalizedTitle;
        }
        return normalizedTitle + "\n\n" + normalizedContent;
    }

    /** null/blank 문자열을 빈 문자열로 정규화한다. */
    private String normalize(String text) {
        return text == null ? "" : text.strip();
    }

    /**
     * 텍스트를 문장 경계에서 나누어 ~500토큰 단위 청크로 분할한다.
     * 현재 청크에 문장을 추가했을 때 목표 토큰 수를 초과하면 새 청크를 시작한다.
     */
    private List<Chunk> splitBySentenceBoundary(String text) {
        List<String> sentences = splitIntoSentences(text);
        List<Chunk> chunks = new ArrayList<>();

        StringBuilder currentChunk = new StringBuilder();
        int currentTokens = 0;
        int chunkIndex = 0;

        for (String sentence : sentences) {
            int sentenceTokens = estimateTokenCount(sentence);

            if (currentTokens + sentenceTokens > TARGET_CHUNK_TOKENS && currentTokens > 0) {
                chunks.add(createChunk(chunkIndex++, currentChunk.toString(), currentTokens));
                currentChunk = new StringBuilder();
                currentTokens = 0;
            }

            if (sentenceTokens > TARGET_CHUNK_TOKENS) {
                chunkIndex = hardSplit(sentence, chunks, currentChunk, currentTokens, chunkIndex);
                currentChunk = new StringBuilder();
                currentTokens = 0;
            } else {
                currentChunk.append(sentence);
                currentTokens += sentenceTokens;
            }
        }

        if (currentTokens > 0) {
            chunks.add(createChunk(chunkIndex, currentChunk.toString(), currentTokens));
        }

        return chunks;
    }

    /**
     * 텍스트를 문장 단위로 분리한다.
     * Java의 BreakIterator(한국어)를 사용하여 마침표, 물음표 등 문장 경계를 감지한다.
     * 문장 분리 실패 시 원문 전체를 1문장으로 취급한다.
     */
    private List<String> splitIntoSentences(String text) {
        List<String> sentences = new ArrayList<>();
        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.KOREAN);
        iterator.setText(text);

        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; end = iterator.next()) {
            String sentence = text.substring(start, end).strip();
            if (!sentence.isEmpty()) {
                sentences.add(sentence);
            }
            start = end;
        }

        if (sentences.isEmpty() && !text.isBlank()) {
            return List.of(text.strip());
        }

        return sentences;
    }

    /**
     * 텍스트의 토큰 수를 근사 추정한다.
     * 한글은 약 2자당 1토큰, 영문/숫자/기호는 약 4자당 1토큰으로 계산한다.
     *
     * @param text 대상 텍스트
     * @return 추정 토큰 수
     */
    public int estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int koreanChars = 0;
        int otherChars = 0;

        for (char c : text.toCharArray()) {
            if (isKorean(c)) {
                koreanChars++;
            } else if (!Character.isWhitespace(c)) {
                otherChars++;
            }
        }

        return (int) Math.ceil(koreanChars / KOREAN_CHARS_PER_TOKEN + otherChars / NON_KOREAN_CHARS_PER_TOKEN);
    }

    /** 한글 음절/자모인지 판별한다. */
    private boolean isKorean(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.HANGUL_SYLLABLES
                || block == Character.UnicodeBlock.HANGUL_JAMO
                || block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO;
    }

    /**
     * 단일 문장이 TARGET_CHUNK_TOKENS를 초과할 때 문자 단위로 강제 분할한다.
     * 앞에 쌓인 currentChunk가 있으면 먼저 flush한 뒤, 긴 문장을 토큰 한도 내로 잘라 청크를 생성한다.
     *
     * @return 갱신된 chunkIndex
     */
    private int hardSplit(String sentence, List<Chunk> chunks, StringBuilder currentChunk,
                          int currentTokens, int chunkIndex) {
        if (currentTokens > 0) {
            chunks.add(createChunk(chunkIndex++, currentChunk.toString(), currentTokens));
        }

        int estimatedCharsPerToken = 3;
        int charsPerChunk = TARGET_CHUNK_TOKENS * estimatedCharsPerToken;
        int start = 0;

        while (start < sentence.length()) {
            int end = Math.min(start + charsPerChunk, sentence.length());
            String part = sentence.substring(start, end);
            int partTokens = estimateTokenCount(part);

            while (partTokens > TARGET_CHUNK_TOKENS && end > start + 1) {
                end--;
                part = sentence.substring(start, end);
                partTokens = estimateTokenCount(part);
            }

            chunks.add(createChunk(chunkIndex++, part, partTokens));
            start = end;
        }

        return chunkIndex;
    }

    /** 청크 객체를 생성한다. strip() 처리를 한 곳에서 담당한다. */
    private Chunk createChunk(int chunkIndex, String text, int tokenCount) {
        return Chunk.builder()
                .chunkIndex(chunkIndex)
                .chunkText(text.strip())
                .tokenCount(tokenCount)
                .build();
    }

    @Getter
    @Builder
    public static class Chunk {
        private final int chunkIndex;
        private final String chunkText;
        private final int tokenCount;
    }
}
