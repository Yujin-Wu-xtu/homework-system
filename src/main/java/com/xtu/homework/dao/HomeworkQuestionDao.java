package com.xtu.homework.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xtu.homework.entity.HomeworkQuestion;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HomeworkQuestionDao extends BaseMapper<HomeworkQuestion> {
}
