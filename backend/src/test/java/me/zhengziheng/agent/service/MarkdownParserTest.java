package me.zhengziheng.agent.service;

import me.zhengziheng.agent.service.MarkdownParser.ParsedSection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MarkdownParser 纯逻辑单测：按标题切分段落块 + 代码围栏保护。
 */
class MarkdownParserTest {

    private final MarkdownParser parser = new MarkdownParser();

    @Test
    void nullOrBlank_returnsEmpty() {
        assertTrue(parser.parse(null).isEmpty());
        assertTrue(parser.parse("").isEmpty());
        assertTrue(parser.parse("   \n  ").isEmpty());
    }

    @Test
    void plainText_noHeading_singleSection() {
        List<ParsedSection> sections = parser.parse("没有标题的正文内容。");
        assertEquals(1, sections.size());
        assertEquals("", sections.get(0).getHeading());
        assertEquals("没有标题的正文内容。", sections.get(0).getBody());
    }

    @Test
    void headings_produceSectionsWithOwnBody() {
        List<ParsedSection> sections = parser.parse("# 一级标题\n第一段内容\n## 二级标题\n第二段内容\n");
        assertEquals(2, sections.size());
        assertEquals("一级标题", sections.get(0).getHeading());
        assertEquals("第一段内容", sections.get(0).getBody());
        assertEquals("二级标题", sections.get(1).getHeading());
        assertEquals("第二段内容", sections.get(1).getBody());
    }

    @Test
    void headingLevels_anyDepthAccepted() {
        List<ParsedSection> sections = parser.parse("###### 六级标题\n内容\n# 回到一级\n更多内容");
        assertEquals(2, sections.size());
        assertEquals("六级标题", sections.get(0).getHeading());
        assertEquals("回到一级", sections.get(1).getHeading());
    }

    @Test
    void codeFence_headingsInsideAreNotSplit() {
        // 代码块里的 `# 注释` 不应被当成标题切块
        String md = "# 标题\n```java\n// 注释\n# 这不是标题\nint x = 1;\n```\n围栏外的正文";
        List<ParsedSection> sections = parser.parse(md);
        assertEquals(1, sections.size(), "代码围栏内的 # 行不得切出新段落");
        assertEquals("标题", sections.get(0).getHeading());
        assertTrue(sections.get(0).getBody().contains("# 这不是标题"), "围栏内内容应留在正文");
        assertTrue(sections.get(0).getBody().contains("围栏外的正文"));
    }

    @Test
    void noSpaceAfterHash_isBodyNotHeading() {
        List<ParsedSection> sections = parser.parse("#not-a-heading");
        assertEquals(1, sections.size());
        assertEquals("", sections.get(0).getHeading());
        assertEquals("#not-a-heading", sections.get(0).getBody());
    }

    @Test
    void onlyHeadings_singleSectionWithLastHeading() {
        // 全是标题行、无正文可归并：最后一段正文为空 → 产出 1 个"最后标题+空正文"的段落（锁定当前行为）
        List<ParsedSection> sections = parser.parse("# A\n# B\n");
        assertEquals(1, sections.size());
        assertEquals("B", sections.get(0).getHeading());
        assertEquals("", sections.get(0).getBody());
    }
}
