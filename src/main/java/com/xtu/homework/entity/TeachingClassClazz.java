package com.xtu.homework.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("teaching_class_clazz")
public class TeachingClassClazz {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teachingClassId;
    private Long clazzId;
}
