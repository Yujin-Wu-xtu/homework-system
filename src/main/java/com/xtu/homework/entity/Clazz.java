package com.xtu.homework.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("clazz")
public class Clazz {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String grade;
    private String major;
    /** 学院（管理层级：学院→专业→班级；老数据可为空，树中归"未分类学院"） */
    private String college;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
