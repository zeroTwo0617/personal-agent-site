package me.zhengziheng.agent.service;

import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Markdown 解析：按标题（# ~ ######）切分为"段落块"，每块带所属标题（作为分块元数据）。
 * 解析策略是 RAG 工程深度的核心谈资——保留语义边界，避免把完整知识点切断。
 */
@Service
public class MarkdownParser {

    @Data
    public static class ParsedSection {
        /** 所属标题，无标题段落为空串（视为"前言"） */
        private String heading;
        /** 段落正文 */
        private String body;
    }

    public List<ParsedSection> parse(String markdown) {
        List<ParsedSection> sections = new ArrayList<>();
        if (markdown == null || markdown.isBlank()) {
            return sections;
        }
        String[] lines = markdown.split("\n", -1);
        StringBuilder body = new StringBuilder();
        String currentHeading = "";
        // 代码围栏（```）内的行一律视为正文：避免把代码里的 `# 注释` 误判成标题切坏语义
        boolean inFence = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                inFence = !inFence;
                body.append(line).append("\n");
                continue;
            }
            if (!inFence && line.matches("^#{1,6}\\s+.*")) {
                if (body.length() > 0) {
                    sections.add(make(currentHeading, body.toString()));
                    body.setLength(0);
                }
                currentHeading = line.replaceFirst("^#{1,6}\\s+", "").trim();
            } else {
                body.append(line).append("\n");
            }
        }
        if (body.length() > 0) {
            sections.add(make(currentHeading, body.toString()));
        }
        if (sections.isEmpty()) {
            sections.add(make("", markdown));
        }
        return sections;
    }

    private ParsedSection make(String heading, String body) {
        ParsedSection s = new ParsedSection();
        s.setHeading(heading);
        s.setBody(body.trim());
        return s;
    }
}
