package com.xtu.homework.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xtu.homework.entity.VerificationCode;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface VerificationCodeDao extends BaseMapper<VerificationCode> {
}
