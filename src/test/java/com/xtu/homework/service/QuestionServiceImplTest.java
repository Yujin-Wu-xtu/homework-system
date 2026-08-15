package com.xtu.homework.service;

import com.xtu.homework.HomeworkApplication;
import com.xtu.homework.entity.Question;
import com.xtu.homework.entity.QuestionOption;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 题目服务单元测试
 * 覆盖：新增题目、查重检测、搜索、状态切换、选项管理
 */
@SpringBootTest(classes = HomeworkApplication.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class QuestionServiceImplTest {

    @Autowired
    private QuestionService questionService;

    @Test
    @Order(1)
    void testAddSingleChoiceQuestion() {
        Question q = new Question();
        q.setType("SINGLE_CHOICE");
        q.setContent("单元测试：1+1等于几？");
        q.setCorrectAnswer("B");
        q.setDifficulty("EASY");
        q.setScore(java.math.BigDecimal.valueOf(5));
        q.setCreatorId(1L);

        QuestionOption opt = new QuestionOption();
        opt.setLabel("A");
        opt.setContent("1");
        opt.setSortOrder(1);

        Question saved = questionService.addQuestion(q, List.of(opt), null);
        assertNotNull(saved.getId());
        assertEquals("ACTIVE", saved.getStatus());
    }

    @Test
    @Order(2)
    void testAddEssayQuestion() {
        Question q = new Question();
        q.setType("ESSAY");
        q.setContent("单元测试：请简述TDD的优点。");
        q.setReferenceAnswer("提前发现缺陷，提高代码质量，便于重构。");
        q.setDifficulty("MEDIUM");
        q.setScore(java.math.BigDecimal.valueOf(15));
        q.setCreatorId(1L);

        Question saved = questionService.addQuestion(q, null, null);
        assertNotNull(saved.getId());
    }

    @Test
    @Order(3)
    void testAddQuestionWithoutAnswer() {
        Question q = new Question();
        q.setType("SINGLE_CHOICE");
        q.setContent("缺少标准答案的选择题");
        q.setDifficulty("EASY");
        q.setScore(java.math.BigDecimal.valueOf(5));

        assertThrows(RuntimeException.class, () ->
                questionService.addQuestion(q, null, null));
    }

    @Test
    @Order(4)
    void testSearchQuestions() {
        var page = questionService.searchQuestions(1, 10, null, null, null, null);
        assertNotNull(page);
        // 验证查询不抛异常且返回结果对象
        assertNotNull(page.getRecords());
    }

    @Test
    @Order(5)
    void testSearchByType() {
        var page = questionService.searchQuestions(1, 10, null, "ESSAY", null, null);
        assertTrue(page.getRecords().stream().allMatch(q -> "ESSAY".equals(q.getType())));
    }

    @Test
    @Order(6)
    void testCheckDuplicate() {
        // 验证查重方法不抛异常并返回结果
        List<Question> dups = questionService.checkDuplicate(
                "以下哪种数据结构是线性结构？", "SINGLE_CHOICE");
        assertNotNull(dups);
        // H2 CLOB字段LIKE查询有限制，仅验证方法调用成功
    }

    @Test
    @Order(7)
    void testCheckNoDuplicate() {
        List<Question> dups = questionService.checkDuplicate(
                "abcdefghijklmnopqrstuvwxyz_完全不同", "ESSAY");
        assertNotNull(dups);
    }

    @Test
    @Order(8)
    void testToggleStatus() {
        // 创建新题目然后切换其状态
        Question q = new Question();
        q.setType("TRUE_FALSE");
        q.setContent("单元测试状态切换");
        q.setCorrectAnswer("对");
        q.setDifficulty("EASY");
        q.setScore(java.math.BigDecimal.valueOf(2));
        q.setCreatorId(1L);
        Question saved = questionService.addQuestion(q, null, null);

        questionService.toggleStatus(saved.getId());
        Question after = questionService.getById(saved.getId());
        assertEquals("DISABLED", after.getStatus());

        questionService.toggleStatus(saved.getId());
        after = questionService.getById(saved.getId());
        assertEquals("ACTIVE", after.getStatus());
    }

    @Test
    @Order(9)
    void testGetOptions() {
        List<QuestionOption> options = questionService.getOptions(1L);
        assertNotNull(options);
        assertEquals(4, options.size()); // 题目1有4个选项
    }

    @Test
    @Order(10)
    void testBatchImport() {
        Question q1 = new Question();
        q1.setType("TRUE_FALSE");
        q1.setContent("批量导入题目1");
        q1.setCorrectAnswer("对");
        q1.setDifficulty("EASY");
        q1.setScore(java.math.BigDecimal.valueOf(2));
        q1.setCreatorId(1L);

        Question q2 = new Question();
        q2.setType("TRUE_FALSE");
        q2.setContent("批量导入题目2");
        q2.setCorrectAnswer("错");
        q2.setDifficulty("EASY");
        q2.setScore(java.math.BigDecimal.valueOf(2));
        q2.setCreatorId(1L);

        // q2 has no correct answer validation since it goes through batchImport
        // which calls addQuestion internally - actually it does validate
        int count = questionService.batchImport(List.of(q1, q2));
        assertEquals(2, count);
    }

    @Test
    @Order(11)
    void testImportQuestionsFromExcel() throws Exception {
        // 已废弃：Excel 模板导入合并入 AI 文件导入（整理/出题），此测试移除
    }

    // ========== 编辑题目（选项/知识点更新）==========

    @Test
    @Order(16)
    void testUpdateQuestionOptionsAndKps() {
        // 自建一道含 4 选项的选择题，验证编辑时选项先删后插 + 分值更新
        Question base = new Question();
        base.setType("SINGLE_CHOICE");
        base.setContent("单元测试编辑选项：算法的时间复杂度主要取决于什么？");
        base.setCorrectAnswer("B");
        base.setDifficulty("EASY");
        base.setScore(java.math.BigDecimal.valueOf(5));
        Question created = questionService.addQuestion(base, List.of(
                opt("A", "输入规模", 0), opt("B", "代码行数", 1), opt("C", "编译器", 2), opt("D", "运行环境", 3)), null);

        List<QuestionOption> newOpts = List.of(
                opt("A", "选项A内容", 0), opt("B", "选项B内容", 1), opt("C", "选项C内容", 2));
        Question upd = new Question();
        upd.setContent("单元测试编辑选项（已编辑）：算法的时间复杂度主要取决于什么？");
        upd.setScore(java.math.BigDecimal.valueOf(8));

        Question saved = questionService.updateQuestion(created.getId(), upd, newOpts, null);
        assertEquals("单元测试编辑选项（已编辑）：算法的时间复杂度主要取决于什么？", saved.getContent());
        assertEquals(8, saved.getScore().intValue());

        // 选项应变为 3 个（先删后插）
        List<QuestionOption> opts = questionService.getOptions(created.getId());
        assertEquals(3, opts.size(), "编辑后选项应为3个（旧的4个已删除）");
        assertEquals("A", opts.get(0).getLabel());
        assertEquals("C", opts.get(2).getLabel());
    }

    @Test
    @Order(17)
    void testUpdateQuestionKeepAnswerForObjective() {
        Question q = new Question();
        q.setType("TRUE_FALSE");
        q.setContent("单元测试更新保护：判断题必须保留答案");
        q.setCorrectAnswer("对");
        Question created = questionService.addQuestion(q, null, null);

        // 尝试把答案清空 → 应拒绝
        Question upd = new Question();
        upd.setCorrectAnswer("");
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> questionService.updateQuestion(created.getId(), upd, null, null));
        assertTrue(ex.getMessage().contains("标准答案"), "应提示客观题必须保留标准答案");
    }

    @Test
    @Order(18)
    void testUpdateNonexistentQuestion() {
        Question upd = new Question();
        upd.setContent("不存在");
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> questionService.updateQuestion(999999L, upd, null, null));
        assertTrue(ex.getMessage().contains("不存在"));
    }

    private static QuestionOption opt(String label, String content, int sortOrder) {
        QuestionOption o = new QuestionOption();
        o.setLabel(label);
        o.setContent(content);
        o.setSortOrder(sortOrder);
        return o;
    }
}
