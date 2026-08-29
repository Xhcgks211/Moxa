package com.moka.agent.common;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prompt 模板加载器：展开片段引用并注入动态变量。
 *
 * <p>支持语法：
 * <ul>
 *   <li>{@code {{include:fragments/foo.md}} 片段引用（支持嵌套，深度上限 5 层）</li>
 *   <li>{@code {{current_date}} 当前日期（yyyy-MM-dd）</li>
 *   <li>{@code {{current_weekday}} 当前星期（中文全称）</li>
 *   <li>{@code {{current_time}} 当前时间（HH:mm:ss）</li>
 * </ul>
 *
 * <p>该组件与 gogo-agent 的实现保持一致，便于业务侧 prompt 直接复用。</p>
 */
public final class PromptLoader {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** prompt 资源根目录（classpath）。 */
    private static final String PROMPT_ROOT = "prompts/";

    /** 片段引用指令，如 {@code {{include:fragments/time-rules.md}}}。 */
    private static final Pattern INCLUDE_PATTERN = Pattern.compile("\\{\\{include:\\s*([\\w\\-./]+)\\s*}}");

    /** 片段嵌套展开的最大层数，超过视为配置错误。 */
    private static final int MAX_INCLUDE_DEPTH = 5;

    /** 主 prompt 缺失时的兼容兜底文案。 */
    private static final String FALLBACK_PROMPT = "You are a helpful assistant.";

    private PromptLoader() {
    }

    /**
     * 加载 prompt 模板并注入动态变量。
     */
    public static String load(String filename) {
        String content = loadResource(PROMPT_ROOT + filename);
        if (content == null || content.isEmpty()) {
            return FALLBACK_PROMPT;
        }
        content = expandIncludes(content, new LinkedHashSet<>(Set.of(filename)), 0);
        LocalDate today = LocalDate.now();
        String weekday = today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.CHINA);
        return content
                .replace("{{current_date}}", today.format(DATE_FORMATTER))
                .replace("{{current_weekday}}", weekday)
                .replace("{{current_time}}", LocalTime.now().format(TIME_FORMATTER));
    }

    /**
     * 递归展开片段引用指令。
     */
    private static String expandIncludes(String content, Set<String> chain, int depth) {
        if (depth > MAX_INCLUDE_DEPTH) {
            throw new IllegalStateException(
                    "prompt 片段嵌套超过 " + MAX_INCLUDE_DEPTH + " 层，引用链：" + String.join(" -> ", chain));
        }
        Matcher matcher = INCLUDE_PATTERN.matcher(content);
        StringBuilder sb = new StringBuilder();
        boolean matched = false;
        while (matcher.find()) {
            matched = true;
            String path = matcher.group(1);
            if (chain.contains(path)) {
                throw new IllegalStateException(
                        "prompt 片段存在环形引用：" + String.join(" -> ", chain) + " -> " + path);
            }
            String fragment = loadResource(PROMPT_ROOT + path);
            if (fragment == null || fragment.isBlank()) {
                throw new IllegalStateException("prompt 片段不存在或为空：" + PROMPT_ROOT + path
                        + "（引用链：" + String.join(" -> ", chain) + "）");
            }
            Set<String> nested = new LinkedHashSet<>(chain);
            nested.add(path);
            String expanded = expandIncludes(fragment.strip(), nested, depth + 1);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(expanded));
        }
        if (!matched) {
            return content;
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String loadResource(String path) {
        try (var is = PromptLoader.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                return "";
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
