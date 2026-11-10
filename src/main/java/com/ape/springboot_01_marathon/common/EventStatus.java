package com.ape.springboot_01_marathon.common;

/**
 * 赛事状态机。
 * <p>
 * 之前状态码散落在各处（0报名中 / 1报名截止 / 2已结束），
 * 各处硬编码数字，导致两个后果：
 * <ul>
 *   <li>缺「比赛中」这一档 —— 报名截止后直接进「已结束」，
 *       没有地方能表达「比赛正在进行，成绩可以录了」；</li>
 *   <li>状态校验写成 {@code status < 0 && status > 2}，
 *       条件永远为 false，等于没有校验。</li>
 * </ul>
 * 现在把状态定义、流转规则、可执行的操作全部收口到这一个类里，
 * Service 层只调用 {@link #assertCanEnterResult} 这类语义化方法，
 * 不再出现裸数字。
 *
 * <h3>状态流转</h3>
 * <pre>
 *   0 报名中 ──截止报名──▶ 1 报名截止 ──开赛──▶ 2 比赛中 ──完赛──▶ 3 已结束
 *       ▲                      │
 *       └──────重新开放────────┘
 * </pre>
 *
 * <h3>各状态允许的操作</h3>
 * <table border="1">
 *   <tr><th>状态</th><th>报名</th><th>检录</th><th>录成绩</th><th>签证书</th></tr>
 *   <tr><td>0 报名中</td><td>可</td><td>不可</td><td>不可</td><td>不可</td></tr>
 *   <tr><td>1 报名截止</td><td>不可</td><td>可</td><td>不可</td><td>不可</td></tr>
 *   <tr><td>2 比赛中</td><td>不可</td><td>可</td><td>可</td><td>不可</td></tr>
 *   <tr><td>3 已结束</td><td>不可</td><td>不可</td><td>不可</td><td>可</td></tr>
 * </table>
 */
public final class EventStatus {

    /** 报名中 */
    public static final int REGISTERING = 0;
    /** 报名截止（等待开赛） */
    public static final int REGISTRATION_CLOSED = 1;
    /** 比赛中（可录入成绩） */
    public static final int ONGOING = 2;
    /** 已结束（可签发证书，成绩只读） */
    public static final int FINISHED = 3;

    private EventStatus() {
    }

    /** 状态码 → 中文名 */
    public static String name(Integer status) {
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case REGISTERING:
                return "报名中";
            case REGISTRATION_CLOSED:
                return "报名截止";
            case ONGOING:
                return "比赛中";
            case FINISHED:
                return "已结束";
            default:
                return "未知";
        }
    }

    /** 是否是合法的状态码 */
    public static boolean isValid(Integer status) {
        return status != null
                && status >= REGISTERING
                && status <= FINISHED;
    }

    /**
     * 校验状态流转是否合法。
     * <p>
     * 允许：报名中 ⇄ 报名截止、报名截止 → 比赛中、比赛中 → 已结束。
     * <p>
     * 不可回退：比赛中 → 报名截止、已结束 → 任何状态。
     * 「已结束」是终态 —— 成绩已经公示，回退会造成排行榜口径不一致。
     *
     * @param from 当前状态
     * @param to   目标状态
     */
    public static void assertTransition(Integer from, Integer to) {
        if (from == null || to == null) {
            throw new BusinessException("赛事状态不能为空");
        }
        if (from.equals(to)) {
            return; // 没变，不算流转
        }
        if (!isValid(to)) {
            throw new BusinessException("非法的赛事状态：" + to);
        }
        if (from == FINISHED) {
            throw new BusinessException("赛事已结束，状态不能再变更");
        }
        if (from == ONGOING && to != FINISHED) {
            throw new BusinessException("比赛进行中，只能推进到「已结束」，不能回退");
        }
        if (from == REGISTRATION_CLOSED && to == REGISTERING) {
            // 允许重新开放报名（截止后反悔），但仅限还没开赛
            return;
        }
        if (from == REGISTRATION_CLOSED && to != ONGOING) {
            throw new BusinessException("报名截止后只能推进到「比赛中」");
        }
        if (from == REGISTERING && to != REGISTRATION_CLOSED) {
            throw new BusinessException("报名中只能推进到「报名截止」");
        }
    }

    // ==================== 各操作的前置校验 ====================

    /** 是否允许报名 */
    public static boolean canRegister(Integer status) {
        return status != null && status == REGISTERING;
    }

    /** 是否允许检录核销 */
    public static boolean canCheckin(Integer status) {
        return status != null && (status == REGISTRATION_CLOSED || status == ONGOING);
    }

    /** 是否允许录入/修改成绩 */
    public static boolean canEnterResult(Integer status) {
        return status != null && status == ONGOING;
    }

    /** 是否允许签发完赛证书 */
    public static boolean canIssueCertificate(Integer status) {
        return status != null && status == FINISHED;
    }

    // ==================== 语义化断言（Service 层直接用） ====================

    /** 报名前置校验 */
    public static void assertCanRegister(Integer status) {
        if (status == null) {
            throw new BusinessException("赛事状态异常，无法报名");
        }
        switch (status) {
            case REGISTRATION_CLOSED:
                throw new BusinessException("该赛事报名已截止");
            case ONGOING:
                throw new BusinessException("比赛正在进行中，无法报名");
            case FINISHED:
                throw new BusinessException("该赛事已结束，无法报名");
            default:
                break;
        }
    }

    /** 检录前置校验 */
    public static void assertCanCheckin(Integer status) {
        if (!canCheckin(status)) {
            if (status != null && status == REGISTERING) {
                throw new BusinessException("该赛事还在报名中，未开赛，无法检录");
            }
            if (status != null && status == FINISHED) {
                throw new BusinessException("赛事已结束，无法检录");
            }
            throw new BusinessException("当前赛事状态不支持检录");
        }
    }

    /** 成绩录入前置校验 */
    public static void assertCanEnterResult(Integer status) {
        if (status == null) {
            throw new BusinessException("赛事状态异常，无法录入成绩");
        }
        if (status == REGISTERING) {
            throw new BusinessException("该赛事还在报名中，请先截止报名并开赛后才能录入成绩");
        }
        if (status == REGISTRATION_CLOSED) {
            throw new BusinessException("该赛事尚未开赛，请先把状态推进到「比赛中」才能录入成绩");
        }
        if (status == FINISHED) {
            throw new BusinessException("赛事已结束，成绩已锁定，不允许再录入或修改");
        }
    }

    /** 证书签发前置校验 */
    public static void assertCanIssueCertificate(Integer status) {
        if (status != null && status == FINISHED) {
            return;
        }
        throw new BusinessException("只有「已结束」的赛事才能签发完赛证书");
    }
}
