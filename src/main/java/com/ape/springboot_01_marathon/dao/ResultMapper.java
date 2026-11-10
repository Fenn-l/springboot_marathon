package com.ape.springboot_01_marathon.dao;

import com.ape.springboot_01_marathon.pojo.ResultRecord;
import com.ape.springboot_01_marathon.pojo.dto.RegistrationQuery;
import com.ape.springboot_01_marathon.pojo.vo.RankVO;
import com.ape.springboot_01_marathon.pojo.vo.ResultVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ResultMapper {

    int insert(ResultRecord record);

    int update(ResultRecord record);

    int deleteById(@Param("id") Long id);

    ResultRecord selectById(@Param("id") Long id);

    /** 一条报名只能有一条成绩，联合唯一索引 uk_registration */
    int existsByRegistrationId(@Param("registrationId") Long registrationId);

    List<ResultVO> selectPage(RegistrationQuery query);

    long countPage(RegistrationQuery query);

    /**
     * 赛事总排名基础数据：按净成绩升序取出全部完赛选手
     * 排名在 Java 层用 index+1 算，避免 MySQL 变量写法，跨库更稳
     */
    List<RankVO> selectRanking(@Param("eventId") Long eventId);

    /** 男女分组排名，SQL 里过滤 gender */
    List<RankVO> selectRankingByGender(@Param("eventId") Long eventId, @Param("gender") Integer gender);

    /** 年龄分组排名，SQL 里算好年龄段标识 */
    List<RankVO> selectRankingByAgeGroup(@Param("eventId") Long eventId);

    /** 统计看板：完赛 / 未完赛 / 退赛人数 */
    int countByFinishStatus(@Param("eventId") Long eventId, @Param("finishStatus") Integer finishStatus);

    /** 统计看板：平均净成绩，SQL 内 AVG 返回 null 时 Service 兜 0 */
    Double avgNetTime(@Param("eventId") Long eventId);

    /**
     * C 端「我的成绩」：按选手 id 查全部成绩，联表带出赛事与报名信息。
     */
    List<com.ape.springboot_01_marathon.pojo.vo.MyResultVO> selectByRunnerId(@Param("runnerId") Long runnerId);

    /**
     * 查某条报名对应的成绩（含赛事信息），用于生成完赛证书。
     */
    com.ape.springboot_01_marathon.pojo.vo.MyResultVO selectForCertificate(@Param("registrationId") Long registrationId);

    /**
     * B 端：某赛事可签发证书的名单（只取已完赛的）。
     */
    List<com.ape.springboot_01_marathon.pojo.vo.MyResultVO> selectCertificateList(@Param("eventId") Long eventId);

    /**
     * B 端：批量签发证书，给已完赛且未签发的记录写 cert_issued。
     *
     * @return 签发条数
     */
    int issueCertificates(@Param("eventId") Long eventId);

    /**
     * 查某条报名的证书是否已签发。
     */
    java.time.LocalDateTime selectCertIssued(@Param("registrationId") Long registrationId);

}

