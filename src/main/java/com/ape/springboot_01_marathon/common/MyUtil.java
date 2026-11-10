package com.ape.springboot_01_marathon.common;

public class MyUtil {

    private MyUtil() {
    }

    /** 秒 -> HH:mm:ss ，12550 -> 03:29:10 */
    public static String formatSeconds(Integer seconds) {
        if (seconds == null || seconds < 0) {
            return "--:--:--";
        }
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    /** 根据赛事距离生成号码布前缀：42.195km -> A，普通 -> N */
    public static String bibPrefix(Double distanceKm) {
        if (distanceKm == null) {
            return "N";
        }
        if (distanceKm >= 40) {
            return "A";
        }
        if (distanceKm >= 20) {
            return "B";
        }
        return "N";
    }

    /** 号码布：前缀 + 4 位序号，A0001 */
    public static String buildBibNumber(String prefix, Integer seq) {
        return String.format("%s%04d", prefix, seq);
    }
}

