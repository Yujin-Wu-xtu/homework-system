package com.xtu.homework.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xtu.homework.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface UserDao extends BaseMapper<User> {
    @Select("SELECT * FROM sys_user WHERE role = 'STUDENT' AND clazz_id IN " +
            "(SELECT clazz_id FROM teaching_class_clazz WHERE teaching_class_id = #{tcId})")
    List<User> findStudentsByTeachingClassId(Long tcId);
}
