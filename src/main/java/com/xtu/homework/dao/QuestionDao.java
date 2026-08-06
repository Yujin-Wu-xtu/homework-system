package com.xtu.homework.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xtu.homework.entity.Question;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QuestionDao extends BaseMapper<Question> {
}
