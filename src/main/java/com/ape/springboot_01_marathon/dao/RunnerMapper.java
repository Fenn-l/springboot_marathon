package com.ape.springboot_01_marathon.dao;

import com.ape.springboot_01_marathon.pojo.Runner;
import com.ape.springboot_01_marathon.pojo.dto.RunnerQuery;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface RunnerMapper {

    int insert(Runner runner);

    int update(Runner runner);

    int deleteById(@Param("id") Long id);

    List<Runner> selectPage(RunnerQuery query);

    long countPage(RunnerQuery query);

    Runner selectById(@Param("id") Long id);

    /** 身份证唯一索引冲突时返回 0，Service 转成友好提示 */
    int existsIdCard(@Param("idCard") String idCard, @Param("excludeId") Long excludeId);

    int countActiveRegistration(@Param("runnerId") Long runnerId);
}
