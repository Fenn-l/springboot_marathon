#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
赛事状态机迁移脚本（status 3 档 -> 4 档）
================================================================
背景
----
旧状态：0报名中 1报名截止 2已结束
新状态：0报名中 1报名截止 2比赛中 3已结束

「比赛中」是新增的一档 —— 报名截止后、比赛结束前，
只有在这个状态才能录入成绩。

迁移规则
--------
1. 旧 status=2（已结束）→ 新 status=3（已结束）
   —— 语义直接对应，单独处理。
2. 旧 status=0/1 且【已有成绩记录】→ 新 status=2（比赛中）
   —— 修复历史脏数据：赛事还在报名中却已经录了成绩，
      这种状态在新模型下自相矛盾，归到「比赛中」最合理。
3. 旧 status=0/1 且【无成绩记录】→ 保持不变。

幂等：脚本可重复执行，已迁移的数据不会被二次改动
（规则 2 只处理「有成绩且状态<2」的记录，
 迁移后状态=2，再次执行不会命中）。

用法：
    python tools/migrate_event_status.py
    python tools/migrate_event_status.py --dry-run    # 只看要改什么，不落库
"""
import argparse
import sys

import pymysql

DB_CONFIG = {
    "host": "127.0.0.1",
    "port": 3306,
    "user": "root",
    "password": "zxcv1234",
    "database": "marathon_db",
    "charset": "utf8mb4",
}

OLD_FINISHED = 2      # 旧：已结束
NEW_FINISHED = 3      # 新：已结束
NEW_ONGOING = 2       # 新：比赛中


def has_result_count(cur, event_id):
    """该赛事下已有多少条成绩"""
    cur.execute(
        """
        SELECT COUNT(*)
        FROM `result` res
        INNER JOIN registration reg ON res.registration_id = reg.id
        WHERE reg.event_id = %s
        """,
        (event_id,),
    )
    return cur.fetchone()[0]


def migrate(dry_run=False):
    conn = pymysql.connect(**DB_CONFIG)
    cur = conn.cursor()
    try:
        cur.execute("SELECT id, event_name, status FROM marathon_event ORDER BY id")
        events = cur.fetchall()

        plan = []
        for eid, name, status in events:
            old_status = status
            new_status = status
            reason = "无需变更"

            if old_status == OLD_FINISHED:
                new_status = NEW_FINISHED
                reason = "旧「已结束」→ 新「已结束」"
            elif old_status is not None and old_status < NEW_ONGOING:
                cnt = has_result_count(cur, eid)
                if cnt > 0:
                    new_status = NEW_ONGOING
                    reason = f"已有 {cnt} 条成绩，脏数据修正为「比赛中」"

            if new_status != old_status:
                plan.append((eid, name, old_status, new_status, reason))

        if not plan:
            print("[OK] 没有需要迁移的赛事")
            return 0

        print("需要迁移的赛事：")
        print("-" * 78)
        for eid, name, o, n, reason in plan:
            print(f"  id={eid:<4} {name[:28]:<30} {o} -> {n}   {reason}")
        print("-" * 78)

        if dry_run:
            print("[DRY-RUN] 未写入数据库")
            return len(plan)

        for eid, name, o, n, reason in plan:
            cur.execute(
                "UPDATE marathon_event SET status = %s WHERE id = %s",
                (n, eid),
            )
        conn.commit()
        print(f"[OK] 已迁移 {len(plan)} 个赛事")
        return len(plan)
    except Exception as e:
        conn.rollback()
        print(f"[ERROR] 迁移失败：{e}")
        raise
    finally:
        cur.close()
        conn.close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="赛事状态机迁移")
    parser.add_argument("--dry-run", action="store_true", help="只打印迁移计划，不落库")
    args = parser.parse_args()

    try:
        sys.exit(0 if migrate(args.dry_run) >= 0 else 1)
    except Exception:
        sys.exit(1)
