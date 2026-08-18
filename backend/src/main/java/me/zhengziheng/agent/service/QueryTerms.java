package me.zhengziheng.agent.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 查询分词：把用户问题拆成可匹配的关键词，供「关键词召回」(ILIKE) 使用。
 *
 * 设计取舍（为什么这么拆）：
 *  - 英文/数字按非词字符切分（"Vue3 ref" -> ["vue3","ref"]），ILIKE 能精确命中专有名词/报错码/API 名；
 *  - 中文 PostgreSQL 默认全文检索无分词器（需 zhparser/pg_jieba 扩展），所以这里按「字」做 bigram
 *    （"如何优化" -> ["如何","何优","优化"]），缓解无中文分词导致的召回死角；
 *  - 向量召回仍用整句 embedding（语义层面），关键词召回补「字面精确命中」，二者融合互补；
 *  - 过滤长度 < 2 的碎片与常见停用词，避免噪声。
 *
 * 该工具是纯逻辑、无 Spring 依赖，可独立单测。
 */
public final class QueryTerms {

    /** 按非字母/数字切分（\p{L}=字母含中文，\p{N}=数字），中文整串作为一段 */
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{L}\\p{N}]+");

    private static final Set<String> STOPWORDS = Set.of(
            "the", "a", "an", "of", "to", "is", "in", "and", "or", "for", "with", "on", "at", "by",
            "的", "了", "是", "在", "和", "与", "或", "也", "都", "我", "你", "他", "这", "那",
            "怎么", "如何", "什么", "为什么", "怎样", "哪些"
    );

    private QueryTerms() {
    }

    public static List<String> extract(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        Set<String> terms = new LinkedHashSet<>();
        String[] rawTokens = TOKEN_SPLIT.split(query.toLowerCase());
        for (String tok : rawTokens) {
            if (tok.isEmpty()) {
                continue;
            }
            if (containsCjk(tok)) {
                // 含中文：整串作为短语 term + 生成字级 bigram
                if (tok.length() >= 2 && !STOPWORDS.contains(tok)) {
                    terms.add(tok);
                }
                for (int i = 0; i + 1 < tok.length(); i++) {
                    String bigram = tok.substring(i, i + 2);
                    if (!STOPWORDS.contains(bigram)) {
                        terms.add(bigram);
                    }
                }
            } else if (tok.length() >= 2 && !STOPWORDS.contains(tok)) {
                // 纯英文/数字整词
                terms.add(tok);
            }
        }
        return new ArrayList<>(terms);
    }

    private static boolean containsCjk(String s) {
        for (int i = 0; i < s.length(); i++) {
            int cp = s.codePointAt(i);
            if (cp >= 0x4E00 && cp <= 0x9FFF) {
                return true;
            }
        }
        return false;
    }
}
