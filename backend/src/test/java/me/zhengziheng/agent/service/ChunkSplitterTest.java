package me.zhengziheng.agent.service;

import me.zhengziheng.agent.service.ChunkSplitter.ChunkUnit;
import me.zhengziheng.agent.service.MarkdownParser.ParsedSection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ChunkSplitter 纯逻辑单测：分块策略（按标题 + 长度二次切 + 重叠窗口）是简历核心谈资，
 * 必须用数据锁死行为，任何改动都要过这些用例。
 */
class ChunkSplitterTest {

    private ParsedSection section(String heading, String body) {
        ParsedSection s = new ParsedSection();
        s.setHeading(heading);
        s.setBody(body);
        return s;
    }

    @Test
    void shortText_noHeading_singleChunk() {
        List<ChunkUnit> units = new ChunkSplitter().split(section("", "Vue3 的响应式基于 Proxy。"), 400, 80);
        assertEquals(1, units.size());
        assertEquals("Vue3 的响应式基于 Proxy。", units.get(0).getContent());
        assertEquals("", units.get(0).getSection());
    }

    @Test
    void headingPrefixIncludedInContent() {
        List<ChunkUnit> units = new ChunkSplitter().split(section("响应式基础", "正文内容不超过上限。"), 400, 80);
        assertEquals(1, units.size());
        assertEquals("响应式基础\n正文内容不超过上限。", units.get(0).getContent());
        assertEquals("响应式基础", units.get(0).getSection());
    }

    @Test
    void longText_secondarySplit_respectsMaxChars() {
        // 60 个字符无标点长串，maxChars=40 → 必须硬切出多块，且每块不超上限
        String body = "a".repeat(60);
        List<ChunkUnit> units = new ChunkSplitter().split(section("", body), 40, 10);
        assertTrue(units.size() >= 2, "超长块应被二次切分");
        for (ChunkUnit u : units) {
            assertTrue(u.getContent().length() <= 40, "每块长度不得超过 maxChars");
        }
    }

    @Test
    void breakPreferSentenceEndPunctuation() {
        // 切分点应优先落在句号后，而不是硬切在句中
        String body = "第一句话结束。第二句话比较长需要被切到下一个块。第三句。";
        List<ChunkUnit> units = new ChunkSplitter().split(section("", body), 15, 5);
        assertTrue(units.get(0).getContent().contains("第一句话结束。"), "首个切分点应落在句号后");
        assertTrue(units.get(0).getContent().length() <= 15);
    }

    @Test
    void overlapWindow_adjacentChunksShareTail() {
        // 无标点时 breakAt 落在窗口末尾，下一块应回退 overlap 字，即块2以块1尾部开头
        String body = "abcdefghijklmnopqrstuvwxyz"; // 26 chars
        List<ChunkUnit> units = new ChunkSplitter().split(section("", body), 10, 4);
        assertTrue(units.size() >= 2);
        String first = units.get(0).getContent();
        String second = units.get(1).getContent();
        assertTrue(second.startsWith(first.substring(first.length() - 4)), "相邻块应存在重叠窗口");
    }

    @Test
    void overlapLargerThanAdvance_preventsInfiniteLoop() {
        // overlap(12) 大于单块前进量(10)：强制 next=breakAt 前移，保证循环终止
        String body = "a".repeat(30);
        List<ChunkUnit> units = new ChunkSplitter().split(section("", body), 10, 12);
        assertEquals(3, units.size());
    }

    @Test
    void emptySection_singleEmptyChunk() {
        // 当前行为：空文本产出 1 个空块（上游 MarkdownParser 会过滤空文档，此处锁定行为）
        List<ChunkUnit> units = new ChunkSplitter().split(section("", ""), 400, 80);
        assertEquals(1, units.size());
        assertEquals("", units.get(0).getContent());
    }

    @Test
    void headingOnlySection_keepsHeadingAsContent() {
        List<ChunkUnit> units = new ChunkSplitter().split(section("仅标题", ""), 400, 80);
        assertEquals(1, units.size());
        assertEquals("仅标题\n", units.get(0).getContent());
    }
}
