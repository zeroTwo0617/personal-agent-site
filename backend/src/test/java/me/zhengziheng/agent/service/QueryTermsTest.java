package me.zhengziheng.agent.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * QueryTerms 分词纯逻辑单测：关键词召回（ILIKE）的 term 生成规则。
 */
class QueryTermsTest {

    @Test
    void nullOrBlank_returnsEmpty() {
        assertTrue(QueryTerms.extract(null).isEmpty());
        assertTrue(QueryTerms.extract("").isEmpty());
        assertTrue(QueryTerms.extract("   ").isEmpty());
    }

    @Test
    void pureEnglish_splitsWords() {
        assertEquals(List.of("vue3", "ref"), QueryTerms.extract("Vue3 ref"));
    }

    @Test
    void englishStopwordsAndShortTokens_filtered() {
        assertEquals(List.of("vue"), QueryTerms.extract("the vue a"));
    }

    @Test
    void chinese_keepsWholeTokenPlusBigrams() {
        // "如何优化"：整词保留；bigram "如何" 是停用词被过滤，保留 "何优"/"优化"
        assertEquals(List.of("如何优化", "何优", "优化"), QueryTerms.extract("如何优化"));
    }

    @Test
    void mixedQuery_termOrderAndDedup() {
        // "什么是" 非停用词（停用词表只有"什么"），保留整词 + bigram "么是"；
        // "vue3" 英文整词；"响应式" 整词 + bigram "响应"/"应式"；重复 term 去重
        List<String> terms = QueryTerms.extract("什么是 Vue3 响应式");
        assertEquals(List.of("什么是", "么是", "vue3", "响应式", "响应", "应式"), terms);
    }

    @Test
    void singleCharChinese_noBigramNoNoise() {
        // 单字中文：整词不足 2 字不保留，也无 bigram → 空
        assertTrue(QueryTerms.extract("的").isEmpty());
    }

    @Test
    void duplicateTerms_deduplicated() {
        assertEquals(List.of("vue"), QueryTerms.extract("vue vue vue"));
    }
}
