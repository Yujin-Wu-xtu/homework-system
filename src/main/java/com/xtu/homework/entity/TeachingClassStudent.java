package com.xtu.homework.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 教学班级-学生关联（选修教学班专用：教师从全部学生中自由选择，静态关系；
 * 必修教学班的学生是动态查询——学生的自然班级 ∈ teaching_class_clazz）
 */
@Data
@TableName("teaching_class_student")
public class TeachingClassStudent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teachingClassId;
    private Long studentId;
}
