package com.xtu.homework.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 题干图片（应用题富文本插图）：上传到 data/question-images/，浏览器经 /question-images/xxx 访问
 */
@Data
@TableName("question_image")
public class QuestionImage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private String fileType;
    private Long uploaderId;
    private LocalDateTime createTime;
}
