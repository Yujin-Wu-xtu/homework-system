package com.xtu.homework.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xtu.homework.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogDao extends BaseMapper<AuditLog> {
}
