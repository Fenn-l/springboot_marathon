package com.ape.springboot_01_marathon.pojo.vo;

import com.ape.springboot_01_marathon.common.MyUtil;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Data
public class EventStatisticsVO {

    private Long eventId;
    private String eventName;

    /** 总报名人数（有效报名） */
    private Integer totalRegister;

    private Integer maleCount;
    private Integer femaleCount;

    private Integer finishCount;
    private Integer unFinishCount;
    private Integer retireCount;

    /** 完赛率，百分比保留两位 */
    private BigDecimal finishRate;

    /**
     * 平均净成绩（秒）。
     * <p>
     * <b>没录任何成绩时为 null，不是 0。</b>
     * 用 0 表示「没有成绩」是错的：0 秒在现实中不可能出现，
     * 前端拿到 0 还会格式化成 "00:00:00"，看起来像「平均成绩是 0」。
     */
    private Integer avgNetTime;

    /**
     * 格式化后的平均净成绩，如 "03:29:10"。
     * 没有成绩时为 null，前端显示 "--"。
     */
    private String avgNetTimeStr;

    /**
     * 完赛率 = 完赛人数 / 总人数 * 100
     * 单独抽出来，Service 直接调，避免各处重复算
     */
    public void calcFinishRate() {
        if (totalRegister == null || totalRegister == 0) {
            this.finishRate = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            return;
        }
        int finish = finishCount == null ? 0 : finishCount;
        this.finishRate = BigDecimal.valueOf(finish)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalRegister), 2, RoundingMode.HALF_UP);
    }

    /**
     * 设置平均净成绩并同步格式化字符串。
     * <p>
     * 传 null 或 &lt;= 0 表示「没有可用成绩」，两个字段都置 null。
     */
    public void setAvgNetTime(Integer seconds) {
        if (seconds == null || seconds <= 0) {
            this.avgNetTime = null;
            this.avgNetTimeStr = null;
            return;
        }
        this.avgNetTime = seconds;
        this.avgNetTimeStr = MyUtil.formatSeconds(seconds);
    }
}