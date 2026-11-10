package com.ape.springboot_01_marathon.dao;

import com.ape.springboot_01_marathon.pojo.Registration;
import com.ape.springboot_01_marathon.pojo.dto.RegistrationQuery;
import com.ape.springboot_01_marathon.pojo.vo.RegistrationVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface RegistrationMapper {

    int insert(Registration registration);

    /**
     * 取消报名：<b>物理删除</b>这一行，不是标记 status=2。
     * <p>
     * 为什么必须物理删除：registration.runner_id 上有外键 REFERENCES runner(id)，
     * 外键约束只关心「行是否存在」，完全不看 status。
     * 只标记 status=2 的话这行还在表里，选手就永远删不掉（数据库报 1451）。
     * <p>
     * 业务上也说得通：取消报名 = 「没报过这个赛事」，留一条 status=2 的记录没意义，
     * 反而挡住了选手档案的删除。
     * <p>
     * 已录入成绩的报名记录不允许取消（Service 层前置校验），所以不会出现成绩被孤立。
     */
    int cancel(@Param("id") Long id);

    int assignBibNumber(@Param("id") Long id, @Param("bibNumber") String bibNumber);

    int markPaid(@Param("id") Long id);

    /** 该赛事该前缀下已分配号码布的最大序号，如 A0012 -> 12 */
    Integer maxBibSeq(@Param("eventId") Long eventId, @Param("prefix") String prefix);

    /** 清空号码布，把 bib_number 置回 NULL */
    int clearBibNumber(@Param("id") Long id);

    List<RegistrationVO> selectPage(RegistrationQuery query);

    long countPage(RegistrationQuery query);

    RegistrationVO selectDetailById(@Param("id") Long id);

    Registration selectById(@Param("id") Long id);

    /** 同一选手同一赛事是否已有记录（联合唯一索引 uk_event_runner 的应用层预检） */
    int existsByEventAndRunner(@Param("eventId") Long eventId, @Param("runnerId") Long runnerId);

    /** 该赛事下有效报名数，生成号码布序号用 */
    int countByEvent(@Param("eventId") Long eventId);

    /** 统计看板：报名人数 + 男女分布 */
    int countRegisterByEvent(@Param("eventId") Long eventId);

    int countRegisterByGender(@Param("eventId") Long eventId, @Param("gender") Integer gender);

    /**
     * 该报名记录是否已有成绩。
     * 用于「取消号码布」前的前置校验：已录入成绩的不允许清号码布，
     * 否则成绩表里的 bib_number 关联会失效。
     */
    int existsResult(@Param("registrationId") Long registrationId);

    /**
     * 选手的<b>全部</b>报名记录数（不限状态）。
     * <p>
     * 删除选手时必须用这个而不是只数 status=1。
     * 因为 registration.runner_id 上有外键约束，<b>不管报名是已报名还是已取消，
     * 只要这条记录存在就禁止删除选手</b>。只查 status=1 会让「只有已取消报名」的选手
     * 绕过检查，然后被数据库抛 1451 外键异常。
     */
    int countAllByRunner(@Param("runnerId") Long runnerId);

    /**
     * 删除选手时清理「没有成绩的历史报名记录」。
     * <p>
     * 用于兼容旧数据：早期 cancel 只标记 status=2，留下了这些行。
     * 新的取消逻辑已改为物理删除，不会再产生这类残留。
     * <p>
     * SQL 里带 NOT EXISTS，确保有成绩的记录绝不会被误删
     * （有成绩说明这个选手的成绩是赛事数据的一部分，不该被随手清掉）。
     */
    int deleteOrphanRegistrations(@Param("runnerId") Long runnerId);

    /** 赛事名下的报名总行数（不限状态），用于删除赛事前判断残留 */
    int countAllByEvent(@Param("eventId") Long eventId);

    /** 删除赛事时清理「没有成绩的历史报名记录」，NOT EXISTS 保证有成绩的不被误删 */
    int deleteOrphanRegistrationsByEvent(@Param("eventId") Long eventId);

    /**
     * C 端「我的报名」：按选手 id 查全部报名记录，联表带出赛事信息。
     * <p>
     * 状态 2（已取消）的记录是物理删除的，这里不会查到；
     * 已退赛（status=3）会保留，因为那是赛事结果的一部分。
     */
    List<com.ape.springboot_01_marathon.pojo.vo.MyRegistrationVO> selectByRunnerId(@Param("runnerId") Long runnerId);

    /**
     * 现场检录核销：把 checkin_time 写成当前时间。
     * <p>
     * 带 status=1 条件做双重保险：已取消的报名不该被核销。
     *
     * @return 影响行数，0 表示该记录不存在或已取消
     */
    int markCheckin(@Param("id") Long id);

    /** 按赛事统计已检录数 */
    int countCheckinByEvent(@Param("eventId") Long eventId);

    /** 按赛事统计应检录数（有效报名数） */
    int countActiveByEvent(@Param("eventId") Long eventId);

    /** 按号码布找报名记录 id（现场检销，选手出示号码布核销） */
    Long selectIdByBib(@Param("bibNumber") String bibNumber);

}
