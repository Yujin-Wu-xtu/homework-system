package com.xtu.homework.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 出题服务：课程材料 → 大模型生成题目草稿（不入库，由管理员预览审核后走常规新增入库）
 * 问答题（含参考答案）为核心场景；选择/判断题由 AI 出题干与选项文本，管理员确认后入库
 */
@Service
@RequiredArgsConstructor
public class QuestionAiService {

    private final DeepSeekClient deepSeekClient;
    private final ObjectMapper objectMapper;

    private static final int MAX_MATERIAL_CHARS = 8000;

    /**
     * 生成题目草稿
     * @param material   课程材料文本
     * @param type       ESSAY/SINGLE_CHOICE/MULTI_CHOICE/TRUE_FALSE/ALL(混合)
     * @param count      生成数量 1-10
     * @param difficulty EASY/MEDIUM/HARD
     */
    public List<Map<String, Object>> generateDrafts(String material, String type, int count, String difficulty) {
        if (material == null || material.isBlank()) {
            throw new RuntimeException("课程材料不能为空（文件可能为扫描版/图片版，无法提取文字，请改用文字版文件或直接粘贴文本）");
        }
        material = material.strip();
        if (material.length() < 20) {
            throw new RuntimeException("材料内容过少（仅 " + material.length() + " 字符），无法生成题目。请确认文件可提取文字——扫描版/图片版 PDF 无法提取文本，请改用文字版 PDF 或直接粘贴文本");
        }
        if (material.length() > MAX_MATERIAL_CHARS) {
            material = material.substring(0, MAX_MATERIAL_CHARS);
        }
        count = Math.min(Math.max(count, 1), 10);
        String difficultyText = switch (difficulty == null ? "MEDIUM" : difficulty) {
            case "EASY" -> "简单";
            case "HARD" -> "困难";
            default -> "中等";
        };

        String systemPrompt = buildPrompt(type, count, difficultyText);
        String raw;
        try {
            raw = deepSeekClient.chat(systemPrompt, material, 0.5);
        } catch (RuntimeException e) {
            throw new RuntimeException("AI 生成失败: " + e.getMessage());
        }
        return parseDrafts(raw, type);
    }

    private String buildPrompt(String type, int count, String difficultyText) {
        String typeDesc = switch (type) {
            case "ESSAY" -> "问答题（主观题，须给出参考答案）";
            case "SINGLE_CHOICE" -> "单选题（四选一，给出标准答案）";
            case "MULTI_CHOICE" -> "多选题（给出标准答案，如 ACD）";
            case "TRUE_FALSE" -> "判断题（答案只填 对 或 错）";
            default -> "混合题型（可含单选/多选/判断/问答，每道题必须带 type 字段）";
        };
        String example;
        if ("ESSAY".equals(type)) {
            example = "{\"type\":\"ESSAY\",\"content\":\"题干\",\"referenceAnswer\":\"参考答案\",\"knowledgePoint\":\"知识点\",\"difficulty\":\"MEDIUM\"}";
        } else if ("TRUE_FALSE".equals(type)) {
            example = "{\"type\":\"TRUE_FALSE\",\"content\":\"题干\",\"correctAnswer\":\"对\",\"knowledgePoint\":\"知识点\",\"difficulty\":\"MEDIUM\"}";
        } else if ("SINGLE_CHOICE".equals(type) || "MULTI_CHOICE".equals(type)) {
            example = "{\"type\":\"" + type + "\",\"content\":\"题干\",\"options\":[{\"label\":\"A\",\"content\":\"选项\"},{\"label\":\"B\",\"content\":\"选项\"},{\"label\":\"C\",\"content\":\"选项\"},{\"label\":\"D\",\"content\":\"选项\"}],\"correctAnswer\":\"B\",\"knowledgePoint\":\"知识点\",\"difficulty\":\"MEDIUM\"}";
        } else {
            example = "{\"type\":\"SINGLE_CHOICE\",\"content\":\"题干\",\"options\":[{\"label\":\"A\",\"content\":\"选项\"},{\"label\":\"B\",\"content\":\"选项\"},{\"label\":\"C\",\"content\":\"选项\"},{\"label\":\"D\",\"content\":\"选项\"}],\"correctAnswer\":\"B\",\"knowledgePoint\":\"知识点\",\"difficulty\":\"MEDIUM\"}";
        }
        return "你是一位课程出题专家。请根据用户提供的课程材料，生成 " + count + " 道" + typeDesc + "，难度" + difficultyText + "。\n"
                + "要求：\n"
                + "1. 题目必须基于材料内容，覆盖不同知识点，不得编造材料中没有的概念；\n"
                + "2. 题干表述完整清晰，适合考试场景；\n"
                + "3. 选择题选项 4 个（标签 A/B/C/D），正确项随机分布；判断题答案只填 对 或 错；问答题参考答案要点化；\n"
                + "4. knowledgePoint 填材料中对应的知识点名称（简洁，3-8 字）；difficulty 填 EASY/MEDIUM/HARD；\n"
                + "5. 严格按 JSON 输出，不要输出任何解释文字。格式：{\"questions\":[" + example + "]}";
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseDrafts(String raw, String fallbackType) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode questions = root.has("questions") ? root.get("questions")
                    : (root.isArray() ? root : null);
            if (questions == null || !questions.isArray() || questions.isEmpty()) {
                throw new RuntimeException("AI 未能基于材料生成题目（返回空结果），请确认材料包含足够文字内容后重试");
            }
            List<Map<String, Object>> drafts = new ArrayList<>();
            for (JsonNode q : questions) {
                Map<String, Object> m = new HashMap<>();
                m.put("type", q.hasNonNull("type") ? normalizeType(q.get("type").asText(), fallbackType) : fallbackType);
                m.put("content", q.hasNonNull("content") ? q.get("content").asText() : "");
                m.put("correctAnswer", q.hasNonNull("correctAnswer") ? q.get("correctAnswer").asText() : "");
                m.put("referenceAnswer", q.hasNonNull("referenceAnswer") ? q.get("referenceAnswer").asText() : "");
                m.put("knowledgePoint", q.hasNonNull("knowledgePoint") ? q.get("knowledgePoint").asText() : "");
                m.put("difficulty", q.hasNonNull("difficulty") ? q.get("difficulty").asText() : "MEDIUM");
                List<Map<String, Object>> options = new ArrayList<>();
                if (q.has("options") && q.get("options").isArray()) {
                    for (JsonNode o : q.get("options")) {
                        Map<String, Object> om = new HashMap<>();
                        om.put("label", o.hasNonNull("label") ? o.get("label").asText() : "");
                        om.put("content", o.hasNonNull("content") ? o.get("content").asText() : "");
                        options.add(om);
                    }
                }
                m.put("options", options);
                drafts.add(m);
            }
            return drafts;
        } catch (Exception e) {
            throw new RuntimeException("AI 返回解析失败: " + e.getMessage());
        }
    }

    /** 模型可能用别名（SHORT_ANSWER/JUDGEMENT/MULTIPLE_CHOICE 等），规范化为系统枚举 */
    private String normalizeType(String t, String fallbackType) {
        String s = t == null ? "" : t.trim().toUpperCase();
        if (s.contains("SHORT") || s.contains("ANSWER") || s.contains("ESSAY") || s.contains("QA") || s.contains("简答") || s.contains("问答")) {
            return "ESSAY";
        }
        if (s.contains("JUDGE") || s.contains("TRUE") || s.contains("FALSE") || s.contains("判断") || s.contains("对错")) {
            return "TRUE_FALSE";
        }
        if (s.contains("MULTI") || s.contains("多选")) {
            return "MULTI_CHOICE";
        }
        if (s.contains("SINGLE") || s.contains("单选")) {
            return "SINGLE_CHOICE";
        }
        return fallbackType == null ? "ESSAY" : fallbackType;
    }
}
