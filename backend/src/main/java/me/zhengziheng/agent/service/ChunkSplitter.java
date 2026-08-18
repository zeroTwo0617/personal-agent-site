package me.zhengziheng.agent.service;

import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 分块器：在 Markdown 解析结果上进一步切分。
 * 策略（组合拳，简历可讲）：
 *  1) 按标题块切（保留语义）
 *  2) 长块再按 maxChars 二次切（默认 400 字）
 *  3) 相邻块重叠 overlap 字（默认 80），避免边界信息丢失、跨块知识点能被完整检索
 * 切分点优先落在换行/句末标点附近，使每块尽量是完整句子。
 */
@Service
public class ChunkSplitter {

    @Data
    public static class ChunkUnit {
        private String section;
        private String content;
    }

    public List<ChunkUnit> split(MarkdownParser.ParsedSection section, int maxChars, int overlap) {
        String heading = (section.getHeading() == null) ? "" : section.getHeading();
        String text = (heading.isEmpty() ? "" : heading + "\n") + section.getBody();

        List<ChunkUnit> out = new ArrayList<>();
        if (text.length() <= maxChars) {
            out.add(unit(heading, text));
            return out;
        }

        int start = 0;
        while (start < text.length()) {
            // 以 maxChars 为窗口取一段，优先在窗口后半段找"换行/句末标点"作为更自然的切分点
            int end = Math.min(start + maxChars, text.length());
            int breakAt = end;
            for (int i = end - 1; i > start + maxChars / 2; i--) {
                char c = text.charAt(i);
                if (c == '\n' || c == '。' || c == '.' || c == '！' || c == '？' || c == '!' || c == '?') {
                    breakAt = i + 1;
                    break;
                }
            }
            out.add(unit(heading, text.substring(start, breakAt).trim()));

            // 下一段从"本段结尾往回退 overlap 字"开始 —— 重叠窗口让跨边界的知识点能被完整检索到
            int next = breakAt - overlap;
            if (next <= start) {
                next = breakAt; // 重叠抵消了前进量则强制前移，防止死循环
            }
            start = next;
            if (start >= text.length()) {
                break;
            }
        }
        return out;
    }

    private ChunkUnit unit(String heading, String content) {
        ChunkUnit u = new ChunkUnit();
        u.setSection(heading);
        u.setContent(content);
        return u;
    }
}
