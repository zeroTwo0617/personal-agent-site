package me.zhengziheng.agent.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 敏感词护栏。
 * - 输入侧：仅拦截"索取本人隐私"的意图（意图词 + 精确正则）；技术词（密码/验证码/微信等）不拦。
 * - 输出侧：对回答中的手机号/邮箱/身份证做脱敏兜底（加前后边界，防误伤订单号/时间戳等长数字串）。
 */
@Component
public class SensitiveGuard {

    /** 索取本人隐私的意图词（逗号分隔配置） */
    private final List<String> intentWords;

    /** 11 位手机号（前后边界） */
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    /** 身份证（17 位数字 + 数字/X） */
    private static final Pattern ID_CARD = Pattern.compile("\\d{17}[\\dXx]");
    /** 邮箱 */
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    public SensitiveGuard(@Value("${rag.guard.sensitive-words:薪资,工资,待遇,期望薪酬,期望薪资,月薪,年薪}") String words) {
        this.intentWords = Arrays.stream(words.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** 输入是否命中"索取本人隐私"意图（命中则拦截，不调 LLM） */
    public boolean isPrivacySolicitation(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        // 精确正则命中：身份证号 / 手机号（用户贴出具体号码/证件号时，视为索取或泄露隐私信息）
        if (ID_CARD.matcher(text).find() || PHONE.matcher(text).find()) {
            return true;
        }
        // 意图词命中
        for (String w : intentWords) {
            if (text.contains(w)) {
                return true;
            }
        }
        return false;
    }

    /** 输出侧脱敏：手机号/邮箱/身份证 → 占位符 */
    public String mask(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String out = PHONE.matcher(text).replaceAll("[联系方式已隐藏]");
        out = EMAIL.matcher(out).replaceAll("[邮箱已隐藏]");
        out = ID_CARD.matcher(out).replaceAll("[证件号已隐藏]");
        return out;
    }

    /** 护栏统一话术 */
    public String refusalMessage() {
        return "这类信息建议与本人直接沟通，面试时可以当面问。";
    }
}
