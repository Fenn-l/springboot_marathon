#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
赛事状态机 + 成绩门禁 专项验收
================================================================
覆盖：
  1. 状态流转规则（允许 / 拒绝）
  2. 成绩录入门禁（只有「比赛中」能录）
  3. 检录门禁
  4. 证书签发门禁
  5. 改密接口
  6. 权限隔离

用法：
    python tools/test_state_machine.py [base_url]
"""
import json
import sys
import urllib.error
import urllib.request

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:18080"

PASS = 0
FAIL = 0
RESULTS = []


def req(method, path, body=None, token=None):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    headers = {"Content-Type": "application/json; charset=UTF-8"}
    if token:
        headers["Authorization"] = "Bearer " + token
    r = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        return json.load(urllib.request.urlopen(r))
    except urllib.error.HTTPError as e:
        try:
            return json.load(e)
        except Exception:
            return {"code": -1, "msg": "HTTP %s" % e.code}


def check(label, cond, detail=""):
    global PASS, FAIL
    if cond:
        PASS += 1
        print(f"  [PASS] {label}")
    else:
        FAIL += 1
        print(f"  [FAIL] {label}  {detail}")
        RESULTS.append((label, detail))


def admin_login():
    r = req("POST", "/api/admin/login", {"username": "admin", "password": "123456"})
    return r["data"]["token"]


def make_event(tok, name, status=0, date="2027-06-01"):
    r = req("POST", "/api/event/add", {
        "eventName": name, "eventType": 2, "eventDate": date,
        "startLocation": "测试起点", "distanceKm": 21.0,
        "quota": 50, "entryFee": 100, "status": status,
    }, tok)
    return r["data"]["id"] if r.get("data") and r["data"].get("id") else None


def set_status(tok, eid, status):
    return req("PUT", "/api/event/update", {"id": eid, "status": status}, tok)


def get_event(tok, eid):
    r = req("GET", f"/api/event/detail/{eid}", None, tok)
    return r.get("data") or {}


def del_event(tok, eid):
    req("DELETE", f"/api/event/delete/{eid}", None, tok)


def main():
    tok = admin_login()
    assert tok, "管理员登录失败"
    print("管理员登录成功\n")

    # ---------- 准备两个测试赛事 ----------
    ev_running = make_event(tok, "SM-可录成绩", status=0, date="2027-06-01")
    ev_closed = make_event(tok, "SM-报名截止", status=0, date="2027-06-02")
    ev_ongoing = make_event(tok, "SM-比赛中", status=0, date="2027-06-03")
    ev_finished = make_event(tok, "SM-已结束", status=0, date="2027-06-04")
    print(f"测试赛事 id: {ev_running}, {ev_closed}, {ev_ongoing}, {ev_finished}\n")

    try:
        print("【1】状态流转规则")
        # 0 -> 1 允许
        r = set_status(tok, ev_running, 1)
        check("报名中 → 报名截止 允许", r.get("code") == 200, str(r))

        # 1 -> 0 允许（重新开放）
        r = set_status(tok, ev_running, 0)
        check("报名截止 → 报名中 允许（重新开放）", r.get("code") == 200, str(r))

        # 0 -> 2 直接跳到比赛中应被拒
        r = set_status(tok, ev_running, 2)
        check("报名中 → 比赛中 被拒（不能跨档）",
              r.get("code") != 200, f"实际 code={r.get('code')} msg={r.get('msg')}")

        # 0 -> 3 已结束应被拒
        r = set_status(tok, ev_running, 3)
        check("报名中 → 已结束 被拒（不能跨档）",
              r.get("code") != 200, f"实际 code={r.get('code')} msg={r.get('msg')}")

        # 非法状态码 9
        r = set_status(tok, ev_running, 9)
        check("非法状态码 9 被拒", r.get("code") != 200, f"msg={r.get('msg')}")

        # 1 -> 2 允许
        set_status(tok, ev_closed, 1)
        r = set_status(tok, ev_closed, 2)
        check("报名截止 → 比赛中 允许", r.get("code") == 200, str(r))

        # 2 -> 1 回退被拒
        r = set_status(tok, ev_closed, 1)
        check("比赛中 → 报名截止 被拒（不能回退）",
              r.get("code") != 200, f"msg={r.get('msg')}")

        # 2 -> 3 允许
        r = set_status(tok, ev_closed, 3)
        check("比赛中 → 已结束 允许", r.get("code") == 200, str(r))

        # 3 是终态
        r = set_status(tok, ev_closed, 2)
        check("已结束 → 比赛中 被拒（终态）",
              r.get("code") != 200, f"msg={r.get('msg')}")
        r = set_status(tok, ev_closed, 0)
        check("已结束 → 报名中 被拒（终态）",
              r.get("code") != 200, f"msg={r.get('msg')}")

        print("\n【2】成绩录入门禁")
        # 拿一个真实报名记录
        regs = req("GET", "/api/register/list?pageNum=1&pageSize=50", None, tok)
        reg_list = regs["data"]["list"]
        if not reg_list:
            check("存在报名记录用于测试", False, "库里没有报名记录")
            return
        base_reg = reg_list[0]

        def input_result(event_id, net=12000):
            return req("POST", "/api/result/input", {
                "registrationId": base_reg["id"],
                "chipTime": net + 200, "netTime": net,
                "finishStatus": 1,
            }, tok)

        # 事件 4 现在是「比赛中」(status=2)，先测已存在的成绩
        r = input_result(base_reg["id"])
        has_result = req("GET", f"/api/result/list?pageNum=1&pageSize=200", None, tok)
        already = any(x["registrationId"] == base_reg["id"]
                      for x in has_result["data"]["list"])
        if already:
            check("已录入过成绩的报名不允许重复录入",
                  r.get("code") != 200, f"msg={r.get('msg')}")
        else:
            check("「比赛中」的赛事允许录入成绩", r.get("code") == 200, str(r))

        # 造一个「报名中」赛事的报名记录，测拒绝
        runners = req("GET", "/api/runner/list?pageNum=1&pageSize=5", None, tok)
        runner_id = runners["data"]["list"][0]["id"]

        # 新建一个报名中的赛事 + 报名
        ev_reg = make_event(tok, "SM-报名中可报名", status=0, date="2027-06-05")
        rr = req("POST", "/api/register/submit",
                 {"eventId": ev_reg, "runnerId": runner_id}, tok)
        new_reg_id = (rr.get("data") or {}).get("registrationId")
        check("在「报名中」赛事报名成功", new_reg_id is not None, str(rr))

        if new_reg_id:
            r = req("POST", "/api/result/input", {
                "registrationId": new_reg_id,
                "chipTime": 12200, "netTime": 12000, "finishStatus": 1,
            }, tok)
            check("「报名中」赛事录成绩被拒",
                  r.get("code") != 200, f"msg={r.get('msg')}")

            # 推进到报名截止，仍应拒绝
            set_status(tok, ev_reg, 1)
            r = req("POST", "/api/result/input", {
                "registrationId": new_reg_id,
                "chipTime": 12200, "netTime": 12000, "finishStatus": 1,
            }, tok)
            check("「报名截止」赛事录成绩被拒",
                  r.get("code") != 200, f"msg={r.get('msg')}")

            # 推进到比赛中，应允许
            set_status(tok, ev_reg, 2)
            r = req("POST", "/api/result/input", {
                "registrationId": new_reg_id,
                "chipTime": 12200, "netTime": 12000, "finishStatus": 1,
            }, tok)
            check("「比赛中」赛事录成绩被允许", r.get("code") == 200, str(r))

            # 结束赛事后，改成绩应被拒
            set_status(tok, ev_reg, 3)
            res_list = req("GET", f"/api/result/list?eventId={ev_reg}", None, tok)
            if res_list["data"]["list"]:
                rid = res_list["data"]["list"][0]["id"]
                r = req("PUT", "/api/result/update", {
                    "id": rid, "registrationId": new_reg_id,
                    "chipTime": 12300, "netTime": 12100, "finishStatus": 1,
                }, tok)
                check("「已结束」赛事改成绩被拒",
                      r.get("code") != 200, f"msg={r.get('msg')}")

            # 检录门禁：报名中的赛事不能检录
            ev_ck = make_event(tok, "SM-检录测试", status=0, date="2027-06-06")
            rr2 = req("POST", "/api/register/submit",
                      {"eventId": ev_ck, "runnerId": runner_id}, tok)
            ck_reg = (rr2.get("data") or {}).get("registrationId")
            if ck_reg:
                r = req("POST", f"/api/checkin?id={ck_reg}", None, tok)
                check("「报名中」赛事检录被拒",
                      r.get("code") != 200, f"msg={r.get('msg')}")
                set_status(tok, ev_ck, 1)
                r = req("POST", f"/api/checkin?id={ck_reg}", None, tok)
                check("「报名截止」赛事检录被允许", r.get("code") == 200, str(r))

            # 证书签发门禁
            r = req("POST", f"/api/certificate/issue?eventId={ev_ck}", None, tok)
            check("非「已结束」赛事签发证书被拒",
                  r.get("code") != 200, f"msg={r.get('msg')}")

        print("\n【3】改密接口")
        # 注册一个新选手账号
        import random
        phone = "139" + str(random.randint(10000000, 99999999))
        # 18 位：前 17 位数字 + 1 位校验位（X 或数字），这里用 610104 + 19950310 + 3 位序号 + 校验位
        idc = "61010419950310" + str(random.randint(100, 999)) + str(random.randint(0, 9))
        r = req("POST", "/api/member/register", {
            "phone": phone, "password": "old1234", "name": "改密测试",
            "gender": 1, "idCard": idc, "birthDate": "1995-03-10",
            "emergencyContact": "家人,13911112222",
        })
        check("注册新账号", r.get("code") == 200, str(r))
        mtok = (r.get("data") or {}).get("token")

        if mtok:
            # 原密码错
            r = req("PUT", "/api/member/password",
                    {"oldPassword": "wrongxx", "newPassword": "new1234"}, mtok)
            check("原密码错误时改密被拒", r.get("code") != 200, f"msg={r.get('msg')}")

            # 新密码太短
            r = req("PUT", "/api/member/password",
                    {"oldPassword": "old1234", "newPassword": "123"}, mtok)
            check("新密码过短被拒", r.get("code") != 200, f"msg={r.get('msg')}")

            # 新旧相同
            r = req("PUT", "/api/member/password",
                    {"oldPassword": "old1234", "newPassword": "old1234"}, mtok)
            check("新旧密码相同被拒", r.get("code") != 200, f"msg={r.get('msg')}")

            # 正常改密
            r = req("PUT", "/api/member/password",
                    {"oldPassword": "old1234", "newPassword": "new1234"}, mtok)
            check("正确原密码改密成功", r.get("code") == 200, str(r))

            # 老密码登录应失败
            r = req("POST", "/api/member/login", {"phone": phone, "password": "old1234"})
            check("改密后旧密码登录失败", r.get("code") != 200, f"msg={r.get('msg')}")

            # 新密码登录应成功
            r = req("POST", "/api/member/login", {"phone": phone, "password": "new1234"})
            check("改密后新密码登录成功", r.get("code") == 200, str(r))

            print("\n【4】管理员开通/重置选手账号")
            # 拿一个 B 端建的选手档案（这类选手默认没有 C 端账号）
            runners = req("GET", "/api/runner/list?pageNum=1&pageSize=10", None, tok)
            rlist = runners["data"]["list"]
            target = None
            for rr in rlist:
                st = req("GET", f"/api/member-account/status/{rr['id']}", None, tok)
                if not st["data"]["exists"]:
                    target = rr
                    break
            if target:
                check("找到没有 C 端账号的选手档案", True,
                      f"runnerId={target['id']} {target['name']}")
                st = req("GET", f"/api/member-account/status/{target['id']}", None, tok)
                check("账号状态接口返回 exists=false", st["data"]["exists"] is False, str(st))
                check("状态接口返回档案手机号", st["data"]["phone"] == target["phone"], str(st))

                r = req("POST", f"/api/member-account/reset/{target['id']}",
                        {"password": "123"}, tok)
                check("管理员重置：密码过短被拒", r.get("code") != 200, f"msg={r.get('msg')}")

                r = req("POST", f"/api/member-account/reset/{target['id']}",
                        {"password": "init1234"}, tok)
                check("管理员为选手开通 C 端账号成功", r.get("code") == 200, str(r))
                check("返回标记为「新建」", (r.get("data") or {}).get("created") is True, str(r))

                r = req("POST", f"/api/member-account/reset/{target['id']}",
                        {"password": "reset1234"}, tok)
                check("账号已存在时转为重置",
                      (r.get("data") or {}).get("created") is False, str(r))

                r = req("POST", "/api/member/login",
                        {"phone": target["phone"], "password": "reset1234"})
                check("管理员重置后可用新密码登录 C 端", r.get("code") == 200, str(r))

                r = req("POST", "/api/member/login",
                        {"phone": target["phone"], "password": "init1234"})
                check("重置后旧密码登录失败", r.get("code") != 200, f"msg={r.get('msg')}")

                # 清理：删掉测试账号，恢复原状
                import pymysql as _pm
                _c = _pm.connect(host="127.0.0.1", port=3306, user="root",
                                 password="zxcv1234", database="marathon_db")
                _cur = _c.cursor()
                _cur.execute("DELETE FROM member_account WHERE runner_id=%s", (target["id"],))
                _c.commit()
                _cur.close()
                _c.close()
                st = req("GET", f"/api/member-account/status/{target['id']}", None, tok)
                check("清理后账号已删除", st["data"]["exists"] is False, str(st))
            else:
                check("所有选手都已开通账号，跳过开通测试", True)

            print("\n【5】权限隔离")
            r = req("GET", "/api/member/profile", None, "fake-member-token")
            check("伪造 Token 访问 C 端被拒", r.get("code") != 200, f"msg={r.get('msg')}")
            r = req("GET", "/api/member/profile", None, tok)
            check("管理员 Token 访问 C 端接口被拒", r.get("code") != 200, f"msg={r.get('msg')}")
            r = req("GET", f"/api/member-account/status/5", None, "fake-admin-token")
            check("伪造 Token 访问账号管理接口被拒", r.get("code") != 200, f"msg={r.get('msg')}")

            # 清理测试账号
            import pymysql
            c = pymysql.connect(host="127.0.0.1", port=3306, user="root",
                                password="zxcv1234", database="marathon_db")
            cur = c.cursor()
            cur.execute("SELECT id FROM runner WHERE phone=%s", (phone,))
            row = cur.fetchone()
            if row:
                cur.execute("DELETE FROM registration WHERE runner_id=%s", (row[0],))
                cur.execute("DELETE FROM member_account WHERE runner_id=%s", (row[0],))
                cur.execute("DELETE FROM runner WHERE id=%s", (row[0],))
            c.commit()
            cur.close()
            c.close()

    finally:
        # 清理测试赛事。
        # ⚠️ 不能只靠接口删：取消报名接口对「已录入成绩」的记录会拒绝
        # （业务规则：成绩是最终结果，不允许通过取消报名来抹掉），
        # 于是赛事就永远删不掉，残留的 SM- 赛事会污染后续测试。
        # 这里直接用 SQL 按顺序删：成绩 → 报名 → 赛事。
        import pymysql as _pm2
        _c2 = _pm2.connect(host="127.0.0.1", port=3306, user="root",
                           password="zxcv1234", database="marathon_db")
        _cur2 = _c2.cursor()
        try:
            _cur2.execute("SELECT id FROM marathon_event WHERE event_name LIKE 'SM-%'")
            for (eid,) in _cur2.fetchall():
                _cur2.execute(
                    """DELETE res FROM `result` res
                       INNER JOIN registration reg ON res.registration_id = reg.id
                       WHERE reg.event_id = %s""", (eid,))
                _cur2.execute("DELETE FROM registration WHERE event_id = %s", (eid,))
                _cur2.execute("DELETE FROM marathon_event WHERE id = %s", (eid,))
            _c2.commit()
            _cur2.execute("SELECT COUNT(*) FROM marathon_event WHERE event_name LIKE 'SM-%'")
            left = _cur2.fetchone()[0]
            print(f"\n测试赛事已清理（残留 {left} 条）")
        finally:
            _cur2.close()
            _c2.close()

    print("\n" + "=" * 50)
    print(f"通过 {PASS}  失败 {FAIL}")
    print("=" * 50)
    if RESULTS:
        print("\n失败明细：")
        for label, detail in RESULTS:
            print(f"  - {label}  {detail}")
    return 0 if FAIL == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
