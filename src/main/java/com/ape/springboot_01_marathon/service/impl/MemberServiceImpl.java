package com.ape.springboot_01_marathon.service.impl;

import com.ape.springboot_01_marathon.common.BusinessException;
import com.ape.springboot_01_marathon.common.EventStatus;
import com.ape.springboot_01_marathon.common.MyUtil;
import com.ape.springboot_01_marathon.common.TokenManager;
import com.ape.springboot_01_marathon.dao.EventMapper;
import com.ape.springboot_01_marathon.dao.MemberAccountMapper;
import com.ape.springboot_01_marathon.dao.RegistrationMapper;
import com.ape.springboot_01_marathon.dao.ResultMapper;
import com.ape.springboot_01_marathon.dao.RunnerMapper;
import com.ape.springboot_01_marathon.pojo.MemberAccount;
import com.ape.springboot_01_marathon.pojo.Registration;
import com.ape.springboot_01_marathon.pojo.Runner;
import com.ape.springboot_01_marathon.pojo.dto.MemberRegisterDTO;
import com.ape.springboot_01_marathon.pojo.vo.EventVO;
import com.ape.springboot_01_marathon.pojo.vo.MyRegistrationVO;
import com.ape.springboot_01_marathon.pojo.vo.MyResultVO;
import com.ape.springboot_01_marathon.pojo.vo.PublicEventVO;
import com.ape.springboot_01_marathon.pojo.vo.RankVO;
import com.ape.springboot_01_marathon.service.MemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * C 端业务实现。
 * <p>
 * <b>与 B 端的关系</b>：共用同一批表，但这一层只做「选手视角」的组装与裁剪，
 * 并且复用 B 端 Service 的核心校验逻辑（如报名的名额/重复校验），
 * 保证两端的数据口径完全一致 —— 这正是调研里那句
 * 「三端共享同一份参赛记录，但按角色控制可见字段」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {

    private final MemberAccountMapper memberAccountMapper;
    private final RunnerMapper runnerMapper;
    private final RegistrationMapper registrationMapper;
    private final ResultMapper resultMapper;
    private final EventMapper eventMapper;
    private final TokenManager tokenManager;
    private final PasswordEncoder passwordEncoder;

    /** 密码最短长度 */
    private static final int MIN_PASSWORD_LENGTH = 6;

    // ==================== 账号 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> register(MemberRegisterDTO dto) {
        if (dto.getPassword() == null || dto.getPassword().length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessException("密码至少 " + MIN_PASSWORD_LENGTH + " 位");
        }
        // 手机号已注册
        if (memberAccountMapper.selectByPhone(dto.getPhone()) != null) {
            throw new BusinessException("该手机号已注册，请直接登录");
        }
        // 身份证已被别的档案占用：说明档案已存在，让用户去登录而不是再报一次错
        if (runnerMapper.existsIdCard(dto.getIdCard(), null) > 0) {
            throw new BusinessException("该身份证已建过档案，请使用原手机号登录");
        }

        // 1) 建选手档案（校验规则与 B 端一致）
        Runner runner = new Runner();
        runner.setName(dto.getName().trim());
        runner.setGender(dto.getGender());
        runner.setIdCard(dto.getIdCard().trim());
        runner.setPhone(dto.getPhone().trim());
        runner.setBirthDate(LocalDate.parse(dto.getBirthDate()));
        runner.setEmergencyContact(dto.getEmergencyContact().trim());
        checkRunner(runner);

        runner.setId(null);
        runner.setCreateTime(null);
        runnerMapper.insert(runner);

        // 2) 建登录账号
        MemberAccount account = new MemberAccount();
        account.setPhone(dto.getPhone().trim());
        account.setPassword(passwordEncoder.encode(dto.getPassword()));
        account.setRunnerId(runner.getId());
        memberAccountMapper.insert(account);

        // 3) 签发选手 Token
        String token = tokenManager.create(TokenManager.SCOPE_MEMBER, runner.getId());
        log.info("C 端选手注册成功 runnerId={}", runner.getId());

        return buildLoginResult(token, runner);
    }

    @Override
    public Map<String, Object> login(String phone, String password) {
        MemberAccount account = memberAccountMapper.selectByPhone(phone == null ? null : phone.trim());
        // 账号不存在与密码错误返回同一句提示，避免被用来探测哪些手机号注册过
        if (account == null || !passwordEncoder.matches(password, account.getPassword())) {
            throw new BusinessException("手机号或密码不正确");
        }
        Runner runner = runnerMapper.selectById(account.getRunnerId());
        if (runner == null) {
            throw new BusinessException("账号异常，请联系赛事组委会");
        }
        String token = tokenManager.create(TokenManager.SCOPE_MEMBER, runner.getId());
        return buildLoginResult(token, runner);
    }

    @Override
    public void logout(String token) {
        tokenManager.remove(token);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long runnerId, String oldPassword, String newPassword) {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessException("新密码至少 " + MIN_PASSWORD_LENGTH + " 位");
        }
        if (newPassword.length() > 30) {
            throw new BusinessException("新密码不能超过 30 位");
        }

        MemberAccount account = memberAccountMapper.selectByRunnerId(runnerId);
        if (account == null) {
            throw new BusinessException("账号异常，请联系赛事组委会");
        }
        // 必须校验原密码：否则拿到 Token 的人就能直接把密码改掉
        if (!passwordEncoder.matches(oldPassword, account.getPassword())) {
            throw new BusinessException("原密码不正确");
        }
        if (passwordEncoder.matches(newPassword, account.getPassword())) {
            throw new BusinessException("新密码不能与原密码相同");
        }

        int rows = memberAccountMapper.updatePassword(runnerId, passwordEncoder.encode(newPassword));
        if (rows == 0) {
            throw new BusinessException("修改失败，账号状态已变更");
        }
        log.info("C 端选手 {} 修改了登录密码", runnerId);
    }

    /** 组装登录返回值：token + 选手资料 */
    private Map<String, Object> buildLoginResult(String token, Runner runner) {
        Map<String, Object> data = new HashMap<>(4);
        data.put("token", token);
        data.put("runnerId", runner.getId());
        data.put("name", runner.getName());
        data.put("phone", runner.getPhone());
        data.put("gender", runner.getGender());
        return data;
    }

    // ==================== 资料 ====================

    @Override
    public Runner profile(Long runnerId) {
        Runner runner = runnerMapper.selectById(runnerId);
        if (runner == null) {
            throw new BusinessException(404, "选手档案不存在");
        }
        return runner;
    }

    @Override
    public boolean updateProfile(Long runnerId, Runner runner) {
        Runner db = profile(runnerId);
        // 只能改自己的，且 id 必须由服务端决定，不能信前端传的
        runner.setId(runnerId);
        runner.setPhone(null);
        runner.setIdCard(null);
        runner.setBirthDate(null);
        runner.setCreateTime(null);
        // 姓名与性别为空时保留原值
        if (runner.getName() == null || runner.getName().isBlank()) {
            runner.setName(db.getName());
        }
        if (runner.getGender() == null) {
            runner.setGender(db.getGender());
        }
        if (runner.getEmergencyContact() == null || runner.getEmergencyContact().isBlank()) {
            runner.setEmergencyContact(db.getEmergencyContact());
        }
        return runnerMapper.update(runner) > 0;
    }

    // ==================== 公开赛事 ====================

    @Override
    public List<PublicEventVO> publicEvents(Long runnerId) {
        // 复用管理端的事件分页查询（一次拉 100 条，C 端列表不分页）
        List<EventVO> events = eventMapper.selectPage(buildEventQuery());
        List<PublicEventVO> list = new ArrayList<>(events.size());
        for (EventVO e : events) {
            list.add(toPublicEvent(e, runnerId));
        }
        return list;
    }

    @Override
    public PublicEventVO publicEventDetail(Long eventId, Long runnerId) {
        EventVO vo = eventMapper.selectDetailById(eventId);
        if (vo == null) {
            throw new BusinessException(404, "赛事不存在");
        }
        return toPublicEvent(vo, runnerId);
    }

    /** 构造查询条件：只要报名中和已结束的赛事 */
    private com.ape.springboot_01_marathon.pojo.dto.EventQuery buildEventQuery() {
        com.ape.springboot_01_marathon.pojo.dto.EventQuery q =
                new com.ape.springboot_01_marathon.pojo.dto.EventQuery();
        q.setPageNum(1);
        q.setPageSize(100);
        q.normalize();
        return q;
    }

    /** EventVO -> PublicEventVO，并补上「我是否已报名」 */
    private PublicEventVO toPublicEvent(EventVO e, Long runnerId) {
        PublicEventVO vo = new PublicEventVO();
        vo.setId(e.getId());
        vo.setEventName(e.getEventName());
        vo.setEventType(e.getEventType());
        vo.setEventDate(e.getEventDate());
        vo.setStartLocation(e.getStartLocation());
        vo.setDistanceKm(e.getDistanceKm());
        vo.setQuota(e.getQuota());
        vo.setRegisteredCount(e.getRegisteredCount());
        vo.setRemainQuota(e.getRemainQuota());
        vo.setEntryFee(e.getEntryFee());
        vo.setStatus(e.getStatus());
        vo.setCreateTime(e.getCreateTime());

        if (e.getEventDate() != null) {
            vo.setDaysLeft((int) ChronoUnit.DAYS.between(LocalDate.now(), e.getEventDate()));
        }
        if (runnerId != null) {
            int exists = registrationMapper.existsByEventAndRunner(e.getId(), runnerId);
            vo.setRegistered(exists > 0);
        }
        return vo;
    }

    // ==================== 我的报名 ====================

    @Override
    public List<MyRegistrationVO> myRegistrations(Long runnerId) {
        List<MyRegistrationVO> list = registrationMapper.selectByRunnerId(runnerId);
        for (MyRegistrationVO vo : list) {
            vo.setCheckedIn(vo.getCheckinTime() != null);
            // 补上我在这场赛事里的报名记录 id（查证书要用）
            vo.setMyRegistrationId(vo.getId());
            // LEFT JOIN 查不到成绩时，MyBatis 仍会造一个空对象出来，
            // 不置空的话前端会把它当成「有成绩但未完赛」，按钮也会跟着错乱。
            if (vo.getResult() != null && vo.getResult().getId() == null) {
                vo.setResult(null);
            }
            if (vo.getResult() != null) {
                vo.getResult().setNetTimeStr(MyUtil.formatSeconds(vo.getResult().getNetTime()));
            }
        }
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long registerEvent(Long runnerId, Long eventId) {
        // 复用管理端报名逻辑（名额校验、重复报名校验、并发兜底都在那边）
        Registration reg = new Registration();
        reg.setEventId(eventId);
        reg.setRunnerId(runnerId);
        return doSubmit(reg);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelMyRegistration(Long runnerId, Long registrationId) {
        // 先确认这条报名确实是「我」的，防止越权取消别人的
        Registration reg = registrationMapper.selectById(registrationId);
        if (reg == null) {
            throw new BusinessException(404, "报名记录不存在");
        }
        if (!reg.getRunnerId().equals(runnerId)) {
            throw new BusinessException(40300, "只能取消自己的报名");
        }
        if (registrationMapper.existsResult(registrationId) > 0) {
            throw new BusinessException("该记录已录入成绩，不允许取消");
        }
        // 走管理端同一套取消逻辑：物理删除 + 报名人数 -1
        int rows = registrationMapper.cancel(registrationId);
        if (rows == 0) {
            throw new BusinessException("取消失败，记录状态已变更");
        }
        eventMapper.decreaseRegisteredCount(reg.getEventId());
        log.info("C 端选手 {} 取消报名 {}", runnerId, registrationId);
        return true;
    }

    // ==================== 我的成绩 ====================

    @Override
    public List<MyResultVO> myResults(Long runnerId) {
        List<MyResultVO> list = resultMapper.selectByRunnerId(runnerId);
        for (MyResultVO vo : list) {
            vo.setNetTimeStr(MyUtil.formatSeconds(vo.getNetTime()));
            vo.setRank(findRank(vo));
        }
        return list;
    }

    @Override
    public MyResultVO certificate(Long runnerId, Long registrationId) {
        // 证书只能查自己的
        Registration reg = registrationMapper.selectById(registrationId);
        if (reg == null) {
            throw new BusinessException(404, "报名记录不存在");
        }
        if (!reg.getRunnerId().equals(runnerId)) {
            throw new BusinessException(40300, "只能查看自己的证书");
        }
        MyResultVO vo = resultMapper.selectForCertificate(registrationId);
        if (vo == null) {
            throw new BusinessException(404, "暂无成绩，无法生成证书");
        }
        if (vo.getFinishStatus() == null || vo.getFinishStatus() != 1) {
            throw new BusinessException("未完赛，无法生成完赛证书");
        }
        vo.setNetTimeStr(MyUtil.formatSeconds(vo.getNetTime()));
        vo.setRank(findRank(vo));
        return vo;
    }

    /** 在该赛事的完赛选手中按净成绩排到第几 */
    private Integer findRank(MyResultVO vo) {
        if (vo.getEventId() == null || vo.getNetTime() == null) {
            return null;
        }
        List<RankVO> all = resultMapper.selectRanking(vo.getEventId());
        for (int i = 0; i < all.size(); i++) {
            RankVO r = all.get(i);
            if (vo.getNetTime() != null && r.getNetTime() != null
                    && vo.getNetTime().equals(r.getNetTime())
                    && (vo.getBibNumber() == null || vo.getBibNumber().equals(r.getBibNumber()))) {
                return i + 1;
            }
        }
        return null;
    }

    // ==================== 公开榜单 ====================

    @Override
    public Map<String, Object> publicRankings(Long eventId, String group) {
        Map<String, Object> data = new HashMap<>(4);
        String g = group == null ? "total" : group;
        switch (g) {
            case "gender": {
                List<RankVO> male = resultMapper.selectRankingByGender(eventId, 1);
                List<RankVO> female = resultMapper.selectRankingByGender(eventId, 2);
                fillRank(male);
                fillRank(female);
                data.put("maleList", male);
                data.put("femaleList", female);
                break;
            }
            case "age": {
                List<RankVO> all = resultMapper.selectRankingByAgeGroup(eventId);
                List<RankVO> below29 = new ArrayList<>();
                List<RankVO> g30to39 = new ArrayList<>();
                List<RankVO> g40to49 = new ArrayList<>();
                List<RankVO> g50up = new ArrayList<>();
                for (RankVO vo : all) {
                    int age = vo.getAge() == null ? 0 : vo.getAge();
                    if (age <= 29) {
                        below29.add(vo);
                    } else if (age <= 39) {
                        g30to39.add(vo);
                    } else if (age <= 49) {
                        g40to49.add(vo);
                    } else {
                        g50up.add(vo);
                    }
                }
                fillRank(below29);
                fillRank(g30to39);
                fillRank(g40to49);
                fillRank(g50up);
                data.put("group_29below", below29);
                data.put("group_30_39", g30to39);
                data.put("group_40_49", g40to49);
                data.put("group_50up", g50up);
                break;
            }
            default: {
                List<RankVO> all = resultMapper.selectRanking(eventId);
                fillRank(all);
                data.put("list", all);
            }
        }
        return data;
    }

    /** 名次 = 列表下标 + 1 */
    private void fillRank(List<RankVO> list) {
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setRank(i + 1);
            list.get(i).setNetTimeStr(MyUtil.formatSeconds(list.get(i).getNetTime()));
        }
    }

    @Override
    public Long resolveRunnerId(String token) {
        // 公开接口用：Token 无效就当游客，不抛异常
        return tokenManager.verify(token, TokenManager.SCOPE_MEMBER);
    }

    // ==================== 私有 ====================

    /**
     * 选手档案校验，规则与 B 端 RunnerServiceImpl.checkRunner 保持一致。
     * <p>
     * 这里单独写一份而不是抽公共方法，是为了不让 C 端依赖管理端的 Service
     * （两端的校验入口分开，将来各自演进不会互相牵制）。
     */
    private void checkRunner(Runner runner) {
        if (runner.getName() == null || runner.getName().isBlank()) {
            throw new BusinessException("姓名不能为空");
        }
        if (runner.getGender() == null || (runner.getGender() != 1 && runner.getGender() != 2)) {
            throw new BusinessException("性别只能是 1男 2女");
        }
        if (runner.getIdCard() == null
                || !runner.getIdCard().matches("^\\d{17}[\\dXx]$")) {
            throw new BusinessException("身份证号格式不正确");
        }
        if (runner.getPhone() == null || !runner.getPhone().matches("^1[3-9]\\d{9}$")) {
            throw new BusinessException("手机号格式不正确");
        }
        if (runner.getEmergencyContact() == null || runner.getEmergencyContact().isBlank()) {
            throw new BusinessException("紧急联系人不能为空");
        }
        if (runner.getBirthDate() == null) {
            throw new BusinessException("出生日期不能为空");
        }
        if (runner.getBirthDate().isAfter(LocalDate.now())) {
            throw new BusinessException("出生日期不能晚于今天");
        }
        int age = Period.between(runner.getBirthDate(), LocalDate.now()).getYears();
        if (age < 16) {
            throw new BusinessException("参赛年龄不得低于 16 周岁，当前 " + age + " 岁");
        }
        if (age > 100) {
            throw new BusinessException("出生日期有误");
        }
    }

    /**
     * 报名落库。与 RegistrationServiceImpl.submit 同逻辑：
     * 赛事状态校验 → 选手存在 → 防重复 → 名额校验 → 落库 → 报名人数 +1。
     */
    private Long doSubmit(Registration reg) {
        com.ape.springboot_01_marathon.pojo.MarathonEvent event =
                eventMapper.selectById(reg.getEventId());
        if (event == null) {
            throw new BusinessException(404, "赛事不存在");
        }
        EventStatus.assertCanRegister(event.getStatus());
        if (runnerMapper.selectById(reg.getRunnerId()) == null) {
            throw new BusinessException(404, "选手不存在");
        }
        if (registrationMapper.existsByEventAndRunner(reg.getEventId(), reg.getRunnerId()) > 0) {
            throw new BusinessException("该选手已报名过此赛事，请勿重复报名");
        }
        int remain = event.getQuota() - event.getRegisteredCount();
        if (remain <= 0) {
            throw new BusinessException("该赛事名额已满");
        }

        reg.setPayStatus(0);
        reg.setStatus(1);
        registrationMapper.insert(reg);

        // SQL 里带 registered_count < quota 条件做并发兜底
        int rows = eventMapper.increaseRegisteredCount(event.getId());
        if (rows == 0) {
            throw new BusinessException("报名失败，名额已满");
        }
        log.info("C 端选手 {} 报名赛事 {} 成功，报名记录 id={}",
                reg.getRunnerId(), reg.getEventId(), reg.getId());
        return reg.getId();
    }
}
