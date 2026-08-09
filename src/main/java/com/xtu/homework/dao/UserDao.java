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

    /** 教学班内学生人数（动态计算：学生的自然班级 ∈ 教学班关联班级）——前端过滤空教学班用 */
    @Select("SELECT COUNT(*) FROM sys_user WHERE role = 'STUDENT' AND clazz_id IN " +
            "(SELECT clazz_id FROM teaching_class_clazz WHERE teaching_class_id = #{tcId})")
    long countStudentsByTeachingClassId(Long tcId);
}
