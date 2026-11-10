/* ==========================================================================
   Stride · 马拉松赛事管理台 —— 前端逻辑
   与后端 26 个接口对应，全部走 /api/**，Token 走 Authorization 头
   ========================================================================== */
const { createApp } = Vue;

createApp({
    data() {
        return {
            logged: !!localStorage.getItem('token'),
            adminName: localStorage.getItem('adminName') || '',
            token: localStorage.getItem('token') || '',
            loginForm: { username: '', password: '' },
            loginError: '',
            logging: false,
            loading: 0,
            saving: false,
            bulkBusy: false,

            sidebarCollapsed: localStorage.getItem('sidebarCollapsed') === '1',
            mobileNavOpen: false,

            active: 'home',
            tabs: [
                { key: 'home', label: '工作台', icon: 'i-grid' },
                { key: 'event', label: '赛事管理', icon: 'i-flag' },
                { key: 'runner', label: '选手管理', icon: 'i-users' },
                { key: 'registration', label: '报名管理', icon: 'i-ticket' },
                { key: 'result', label: '成绩排名', icon: 'i-trophy' }
            ],
            /** 现场运营分组：B 端专属能力，选手端没有对应入口 */
            opsTabs: [
                { key: 'checkin', label: '现场检录', icon: 'i-shield' },
                { key: 'certificate', label: '证书签发', icon: 'i-medal' }
            ],
            EVENT_TYPE: { 1: '全程马拉松', 2: '半程马拉松', 3: '迷你跑' },
            // 赛事状态机：0报名中 → 1报名截止 → 2比赛中 → 3已结束
            // 与后端 common/EventStatus.java 保持一致
            EVENT_STATUS: { 0: '报名中', 1: '报名截止', 2: '比赛中', 3: '已结束' },
            REG_STATUS: { 1: '已报名', 2: '已取消', 3: '退赛' },
            FINISH_STATUS: { 1: '完赛', 2: '未完赛', 3: '退赛' },

            toasts: [],
            toastSeq: 0,
            dialog: null,
            form: {},
            errors: {},
            /** 管理员重置选手密码弹窗的上下文 */
            resetPwdInfo: { runnerId: null, exists: false, phone: '', runnerName: '' },
            confirmBox: null,
            drawer: null,
            runnerPick: '',
            selectedRegIds: [],

            events: [],
            allEvents: [],
            eventPages: 0,
            eventTotal: 0,
            eventQuery: { pageNum: 1, pageSize: 10, eventName: '', eventType: null, status: null, startDate: '', endDate: '' },
            eventSort: { key: '', dir: 'asc' },

            runners: [],
            allRunners: [],
            runnerPages: 0,
            runnerTotal: 0,
            runnerQuery: { pageNum: 1, pageSize: 10, name: '', phone: '', gender: null },

            regs: [],
            regPages: 0,
            regTotal: 0,
            unpaidTotal: 0,
            regQuery: { pageNum: 1, pageSize: 10, eventId: null, name: '', payStatus: null, status: null },

            rankEventId: null,
            rankTab: 'all',
            rankAll: [],
            rankMale: [],
            rankFemale: [],
            stats: null,
            ageGroups: [],

            resultRecords: [],
            resultPages: 0,
            resultTotal: 0,
            resultQuery: { pageNum: 1, pageSize: 10 },

            // 现场检录
            checkinEventId: null,
            checkinInput: '',
            checkinProgress: { total: 0, checked: 0, unchecked: 0, percent: 0 },
            checkinResult: null,

            // 证书签发
            certEventId: null,
            certList: [],
            certPreview: null
        };
    },

    computed: {
        adminInitial() {
            return (this.adminName || '管').slice(0, 1);
        },

        dialogTitle() {
            const map = {
                event: this.form.id ? '编辑赛事' : '新增赛事',
                runner: this.form.id ? '编辑选手' : '新增选手',
                submit: '提交报名',
                bib: '分配号码布',
                result: this.form.resultId ? '修改成绩' : '录入成绩',
                resetPwd: this.resetPwdInfo.exists ? '重置登录密码' : '开通 C 端账号'
            };
            return map[this.dialog] || '';
        },

        dialogSub() {
            const map = {
                event: this.form.id ? '修改赛事基础信息与报名窗口' : '创建一场新的赛事',
                runner: this.form.id ? '修改选手档案信息' : '建立选手档案',
                submit: '为选手报名参赛赛事',
                bib: '号码布在比赛日现场使用',
                result: this.form.resultId ? '修正已录入的成绩' : '录入该选手的比赛成绩'
            };
            return map[this.dialog] || '';
        },

        pageMeta() {
            const map = {
                home: {
                    eyebrow: 'OVERVIEW', title: '比赛日工作台',
                    desc: '先看全局，再下钻到报名、号码布和成绩。'
                },
                event: {
                    eyebrow: 'EVENTS', title: '赛事管理',
                    desc: '维护赛程、名额、费用与报名窗口。',
                    action: { label: '新增赛事', run: () => this.openEventForm() }
                },
                runner: {
                    eyebrow: 'RUNNERS', title: '选手档案',
                    desc: '建档、检索，身份证与手机号已做脱敏展示。',
                    action: { label: '新增选手', run: () => this.openRunnerForm() }
                },
                registration: {
                    eyebrow: 'ENTRIES', title: '报名管理',
                    desc: '提交报名、确认支付、分配号码布、录入成绩。',
                    action: { label: '提交报名', run: () => this.openSubmit() }
                },
                result: {
                    eyebrow: 'RESULTS', title: '成绩与排名',
                    desc: '总排名、性别组、年龄组，以及成绩明细。'
                },
                checkin: {
                    eyebrow: 'ON-SITE', title: '现场检录',
                    desc: '按号码布核销参赛资格，只显示当前岗位需要的任务。'
                },
                certificate: {
                    eyebrow: 'CERTIFICATE', title: '证书签发',
                    desc: '为完赛选手批量签发电子完赛证书，并导出证书名单。'
                }
            };
            return map[this.active] || map.home;
        },

        openEvents() {
            return this.allEvents.filter(e => e.status === 0);
        },

        /**
         * 赛事表单里「状态」下拉的候选值。
         * <p>
         * 只列出从当前状态**合法可达**的状态，避免用户选一个后端会拒绝的组合：
         * <ul>
         *   <li>新建时只有「报名中」可选 —— 赛事都是先开放报名；</li>
         *   <li>报名中 → 可选 报名截止 / 报名中（不变）；</li>
         *   <li>报名截止 → 可选 比赛中 / 报名中（重新开放）/ 报名截止；</li>
         *   <li>比赛中 → 只能推进到 已结束；</li>
         *   <li>已结束 → 终态，一个选项都不给（后端也会拒绝任何变更）。</li>
         * </ul>
         * 规则与后端 common/EventStatus.assertTransition 一致。
         */
        eventStatusOptions() {
            const S = { REGISTERING: 0, CLOSED: 1, ONGOING: 2, FINISHED: 3 };
            const cur = this.form && this.form.status != null ? this.form.status : null;
            const all = [
                { value: S.REGISTERING, label: '报名中' },
                { value: S.CLOSED, label: '报名截止' },
                { value: S.ONGOING, label: '比赛中' },
                { value: S.FINISHED, label: '已结束' }
            ];
            // 新建赛事：默认报名中，只能选报名中
            if (!this.form.id) {
                return all.filter(o => o.value === S.REGISTERING);
            }
            switch (cur) {
                case S.REGISTERING:
                    return all.filter(o => o.value === S.REGISTERING || o.value === S.CLOSED);
                case S.CLOSED:
                    return all.filter(o => o.value === S.REGISTERING
                        || o.value === S.CLOSED || o.value === S.ONGOING);
                case S.ONGOING:
                    return all.filter(o => o.value === S.ONGOING || o.value === S.FINISHED);
                case S.FINISHED:
                    // 终态：保持显示当前值，但不给任何可切换的目标
                    return all.filter(o => o.value === S.FINISHED);
                default:
                    return all.filter(o => o.value === S.REGISTERING);
            }
        },

        /** 当前赛事状态是否允许录入成绩（只有「比赛中」可以） */
        canEnterResult(eventStatus) {
            return eventStatus === 2;
        },

        filteredRunners() {
            const q = (this.runnerPick || '').trim().toLowerCase();
            if (!q) return this.allRunners;
            return this.allRunners.filter(r => {
                return (r.name && r.name.toLowerCase().indexOf(q) >= 0)
                    || (r.idCard && r.idCard.slice(-4).toLowerCase().indexOf(q) >= 0);
            });
        },

        overview() {
            const list = this.allEvents || [];
            const totalQuota = list.reduce((s, e) => s + (e.quota || 0), 0);
            const totalRegs = list.reduce((s, e) => s + (e.registeredCount || 0), 0);
            const remain = list.reduce((s, e) => s + (e.remainQuota || 0), 0);
            const feeSum = list.reduce((s, e) => {
                return s + Number(e.entryFee || 0) * Number(e.registeredCount || 0);
            }, 0);
            return {
                totalEvents: list.length,
                openEvents: list.filter(e => e.status === 0).length,
                totalRegs: totalRegs,
                totalQuota: totalQuota,
                remain: remain,
                fillRate: totalQuota ? Math.round(totalRegs * 100 / totalQuota) : 0,
                feeSum: feeSum.toFixed(0)
            };
        },

        /** 未支付报名数，用于侧栏红点。unpaidTotal 来自全局统计，不受当前分页影响 */
        unpaidCount() {
            return this.unpaidTotal;
        },

        /* ---------- 图表数据 ---------- */

        /** 名额占用率条形图 */
        quotaBars() {
            const colors = ['var(--c1)', 'var(--c2)', 'var(--c3)', 'var(--c4)', 'var(--c5)', 'var(--c6)'];
            return this.allEvents.slice(0, 6).map((e, i) => ({
                id: e.id,
                name: e.eventName,
                used: e.registeredCount || 0,
                quota: e.quota || 0,
                pct: this.quotaPercent(e),
                color: e.status === 2 ? 'var(--ink-4)' : colors[i % colors.length]
            }));
        },

        /** 赛事类型分布环形图 */
        typeDonut() {
            const colors = { 1: 'var(--c1)', 2: 'var(--c2)', 3: 'var(--c3)' };
            const counts = { 1: 0, 2: 0, 3: 0 };
            this.allEvents.forEach(e => {
                if (counts[e.eventType] !== undefined) counts[e.eventType]++;
            });
            return this.buildDonut(
                Object.keys(counts).map(k => ({
                    label: this.EVENT_TYPE[k],
                    value: counts[k],
                    color: colors[k]
                }))
            );
        },

        /** 性别构成环形图 */
        genderDonut() {
            const male = (this.stats && this.stats.maleCount) || 0;
            const female = (this.stats && this.stats.femaleCount) || 0;
            return this.buildDonut([
                { label: '男子', value: male, color: 'var(--c4)' },
                { label: '女子', value: female, color: 'var(--c2)' }
            ]);
        },

        /** 净成绩分布直方图 */
        timeHistogram() {
            const rows = (this.rankAll || []).filter(r => r.netTime);
            if (!rows.length) return { bars: [] };

            const times = rows.map(r => r.netTime).sort((a, b) => a - b);
            const min = times[0];
            const max = times[times.length - 1];
            const realSpan = max - min;

            // 成绩高度集中时（跨度 < 60 秒）就只画一档，否则分档会全是空桶
            if (realSpan < 60) {
                return {
                    bars: [{
                        label: this.formatSeconds(min),
                        short: this.formatSeconds(min).slice(0, 5),
                        count: times.length,
                        height: 100,
                        title: this.formatSeconds(min) + '：' + times.length + ' 人'
                    }]
                };
            }

            // 按实际跨度分 3~6 档，每档至少 1 分钟
            const bucketCount = Math.min(6, Math.max(3, Math.ceil(realSpan / 900) + 1));
            const bucketSize = Math.max(60, Math.ceil(realSpan / bucketCount / 60) * 60);

            const buckets = [];
            for (let i = 0; i < bucketCount; i++) {
                buckets.push({ from: min + i * bucketSize, count: 0 });
            }
            times.forEach(t => {
                let idx = Math.floor((t - min) / bucketSize);
                if (idx >= bucketCount) idx = bucketCount - 1;
                if (idx < 0) idx = 0;
                buckets[idx].count++;
            });

            const maxCount = Math.max.apply(null, buckets.map(b => b.count)) || 1;
            return {
                bars: buckets.map(b => {
                    const label = this.formatSeconds(b.from);
                    return {
                        label: label + ' 起',
                        short: label.slice(0, 5),
                        count: b.count,
                        height: Math.max(4, Math.round(b.count * 100 / maxCount)),
                        title: label + ' 起：' + b.count + ' 人'
                    };
                })
            };
        },

        /** 领奖台：前 3 名，按名次排序，2-1-3 排列 */
        podium() {
            const top = (this.rankAll || []).slice(0, 3);
            if (!top.length) return [];
            // 展示顺序 2 → 1 → 3，第一名居中且台阶最高
            return top.slice().sort((a, b) => {
                const pa = a.rank === 1 ? 0 : (a.rank === 2 ? -1 : 1);
                const pb = b.rank === 1 ? 0 : (b.rank === 2 ? -1 : 1);
                return pa - pb;
            });
        },

        genderGroups() {
            return [
                { key: 'm', label: '男子组', list: this.rankMale },
                { key: 'f', label: '女子组', list: this.rankFemale }
            ];
        },

        /* ---------- 排序 ---------- */
        sortedEvents() {
            const key = this.eventSort.key;
            if (!key) return this.events;
            const dir = this.eventSort.dir === 'asc' ? 1 : -1;
            return this.events.slice().sort((a, b) => {
                let x = a[key], y = b[key];
                if (key === 'quotaPercent') {
                    x = this.quotaPercent(a); y = this.quotaPercent(b);
                }
                if (typeof x === 'string' || typeof y === 'string') {
                    return String(x).localeCompare(String(y), 'zh-CN') * dir;
                }
                return ((x || 0) - (y || 0)) * dir;
            });
        },

        /* ---------- 分页页码 ---------- */
        eventPageList() { return this.pageList(this.eventQuery.pageNum, this.eventPages); },
        runnerPageList() { return this.pageList(this.runnerQuery.pageNum, this.runnerPages); },
        regPageList() { return this.pageList(this.regQuery.pageNum, this.regPages); },
        resultPageList() { return this.pageList(this.resultQuery.pageNum, this.resultPages); },

        allSelected() {
            return this.regs.length > 0 && this.selectedRegIds.length === this.regs.length;
        }
    },

    mounted() {
        document.addEventListener('keydown', this.onKey);
        window.addEventListener('hashchange', this.onHashChange);
        if (this.logged) {
            this.bootstrap();
        }
    },

    unmounted() {
        document.removeEventListener('keydown', this.onKey);
        window.removeEventListener('hashchange', this.onHashChange);
    },

    methods: {
        /* ==================== 通用 ==================== */

        onKey(e) {
            if (e.key === 'Escape') {
                this.closeDialog();
                this.confirmBox = null;
                this.closeDrawer();
            }
        },

        /** hash 变化时切模块，支持直接访问 #event / #result */
        onHashChange() {
            const key = this.readHashTab();
            if (key && key !== this.active) {
                this.switchTab(key);
            }
        },

        /** 从 location.hash 读出模块名，无效则返回 null */
        readHashTab() {
            const raw = (window.location.hash || '').replace('#', '');
            const keys = this.tabs.map(t => t.key).concat(this.opsTabs.map(t => t.key));
            return keys.indexOf(raw) >= 0 ? raw : null;
        },

        /** 构造环形图的分段 dash / offset */
        buildDonut(items) {
            const total = items.reduce((s, i) => s + i.value, 0);
            const C = 2 * Math.PI * 52;
            let acc = 0;
            const segments = items.map(it => {
                const ratio = total ? it.value / total : 0;
                const len = ratio * C;
                const seg = {
                    label: it.label,
                    value: it.value,
                    color: it.color,
                    pct: total ? Math.round(ratio * 100) : 0,
                    dash: len + ' ' + (C - len),
                    offset: -acc
                };
                acc += len;
                return seg;
            });
            return { total: total, segments: segments };
        },

        /** 生成页码序列，最多显示 7 个，中间用 ... 省略 */
        pageList(current, total) {
            if (!total || total <= 1) return [1];
            const list = [];
            const push = p => list.push(p);
            if (total <= 7) {
                for (let i = 1; i <= total; i++) push(i);
                return list;
            }
            push(1);
            if (current > 3) push('...');
            const start = Math.max(2, current - 1);
            const end = Math.min(total - 1, current + 1);
            for (let i = start; i <= end; i++) push(i);
            if (current < total - 2) push('...');
            push(total);
            return list;
        },

        async request(url, options) {
            const opt = options || {};
            const headers = { 'Content-Type': 'application/json' };
            if (!opt.skipAuth && this.token) {
                headers['Authorization'] = 'Bearer ' + this.token;
            }
            this.loading += 1;
            try {
                const resp = await fetch(url, {
                    method: opt.method || 'GET',
                    headers: headers,
                    body: opt.body ? JSON.stringify(opt.body) : undefined
                });
                let json;
                try {
                    json = await resp.json();
                } catch (e) {
                    throw new Error('服务器返回了非 JSON 响应（HTTP ' + resp.status + '）');
                }
                if (json.code !== 200) {
                    if (json.code === 40100 || json.code === 40102) {
                        this.clearLogin();
                        throw new Error('登录已过期，请重新登录');
                    }
                    throw new Error(json.msg || '请求失败');
                }
                return json.data;
            } catch (e) {
                if (e instanceof TypeError) {
                    throw new Error('无法连接服务器，请确认后端已启动');
                }
                throw e;
            } finally {
                this.loading = Math.max(0, this.loading - 1);
            }
        },

        toast(text, type) {
            const id = ++this.toastSeq;
            this.toasts.push({ id: id, text: text, type: type || 'ok' });
            setTimeout(() => {
                this.toasts = this.toasts.filter(t => t.id !== id);
            }, 3200);
        },

        fail(e) {
            this.toast(e && e.message ? e.message : String(e), 'err');
        },

        ask(title, text, ok) {
            this.confirmBox = { title: title, text: text, ok: ok };
        },

        runConfirm() {
            const fn = this.confirmBox && this.confirmBox.ok;
            this.confirmBox = null;
            if (fn) fn();
        },

        buildQuery(obj) {
            const q = new URLSearchParams();
            Object.keys(obj).forEach(k => {
                const v = obj[k];
                if (v !== null && v !== undefined && v !== '') {
                    q.append(k, v);
                }
            });
            return q.toString();
        },

        /* ==================== 登录 / 会话 ==================== */

        async bootstrap() {
            try {
                // 服务端 Token 存在内存里，重启即失效。先探一次 /api/admin/info，
                // 避免拿一个过期 Token 进入控制台后处处报错。
                await this.request('/api/admin/info');
            } catch (e) {
                if (this.logged) {
                    this.clearLogin();
                    this.toast('登录状态已失效，请重新登录', 'err');
                }
                return;
            }
            await this.loadAllEvents();
            this.loadEvents();
            this.loadUnpaidTotal();
            // 带 hash 直接进对应模块（如 #result）
            const hashTab = this.readHashTab();
            if (hashTab && hashTab !== 'home') {
                this.switchTab(hashTab);
            }
        },

        async doLogin() {
            this.loginError = '';
            if (!this.loginForm.username || !this.loginForm.password) {
                this.loginError = '请输入账号和密码';
                return;
            }
            this.logging = true;
            try {
                const data = await this.request('/api/admin/login', {
                    method: 'POST',
                    body: this.loginForm,
                    skipAuth: true
                });
                this.token = data.token;
                this.adminName = data.adminName;
                localStorage.setItem('token', data.token);
                localStorage.setItem('adminName', data.adminName);
                this.logged = true;
                this.loginForm = { username: '', password: '' };
                this.active = 'home';
                this.toast('欢迎回来，' + this.adminName);
                await this.bootstrap();
            } catch (e) {
                this.loginError = e.message || '登录失败';
            } finally {
                this.logging = false;
            }
        },

        askLogout() {
            this.ask('退出登录', '确定离开管理台？\n当前 Token 会立即失效。', () => this.doLogout());
        },

        async doLogout() {
            try {
                await this.request('/api/admin/logout', { method: 'POST' });
            } catch (e) {
                // 服务端已经不在了也无所谓，本地状态一定要清
            }
            this.clearLogin();
            this.toast('已退出登录', 'info');
        },

        clearLogin() {
            localStorage.removeItem('token');
            localStorage.removeItem('adminName');
            this.token = '';
            this.adminName = '';
            this.logged = false;
            this.active = 'home';
            this.events = [];
            this.allEvents = [];
            this.runners = [];
            this.regs = [];
            this.selectedRegIds = [];
            this.rankAll = [];
            this.stats = null;
            this.closeDrawer();
        },

        /* ==================== 导航 ==================== */

        switchTab(key) {
            this.active = key;
            this.mobileNavOpen = false;
            // 同步到 hash：刷新后停在同一个模块，链接也可以直接分享
            if (window.location.hash !== '#' + key) {
                window.history.replaceState(null, '', '#' + key);
            }
            if (key === 'home') {
                this.loadAllEvents();
            } else if (key === 'event') {
                this.loadEvents();
            } else if (key === 'runner') {
                this.loadRunners();
                this.loadAllRunners();
            } else if (key === 'registration') {
                this.loadAllEvents();
                this.loadAllRunners();
                this.loadRegs();
            } else if (key === 'result') {
                this.loadAllEvents().then(() => {
                    if (this.rankEventId === null && this.allEvents.length > 0) {
                        this.rankEventId = this.defaultRankEventId();
                    }
                    this.loadRankings();
                });
            } else if (key === 'checkin') {
                this.loadAllEvents().then(() => {
                    if (this.checkinEventId === null) {
                        // 默认选报名人数最多的那场，检录进度才有意义
                        this.checkinEventId = this.defaultRankEventId();
                    }
                    this.loadCheckinProgress();
                });
            } else if (key === 'certificate') {
                this.loadAllEvents().then(() => {
                    if (this.certEventId === null) {
                        // 默认选报名人数最多的那场，才可能有完赛成绩可签发
                        this.certEventId = this.defaultRankEventId();
                    }
                    this.loadCertList();
                });
            }
        },

        /** 默认选报名人数最多的赛事，比「最新的一场」更可能有成绩可看 */
        defaultRankEventId() {
            const list = this.allEvents || [];
            if (!list.length) return null;
            let best = list[0];
            for (const e of list) {
                if ((e.registeredCount || 0) > (best.registeredCount || 0)) best = e;
            }
            return best.id;
        },

        reloadCurrent() {
            const key = this.active;
            this.selectedRegIds = [];
            if (key === 'home') { this.loadAllEvents(); this.loadEvents(); }
            else if (key === 'event') { this.loadEvents(); this.loadAllEvents(); }
            else if (key === 'runner') { this.loadRunners(); this.loadAllRunners(); }
            else if (key === 'registration') { this.loadRegs(); this.loadAllEvents(); this.loadAllRunners(); }
            else if (key === 'result') { this.loadRankings(); }
            else if (key === 'checkin') { this.loadCheckinProgress(); }
            else if (key === 'certificate') { this.loadCertList(); }
            this.toast('已刷新', 'info');
        },

        /* ==================== 赛事 ==================== */

        async loadAllEvents() {
            try {
                const data = await this.request('/api/event/list?pageNum=1&pageSize=100');
                this.allEvents = data.list || [];
            } catch (e) {
                this.fail(e);
            }
        },

        async loadEvents() {
            try {
                const data = await this.request('/api/event/list?' + this.buildQuery(this.eventQuery));
                this.events = data.list || [];
                this.eventPages = data.pages || 0;
                this.eventTotal = data.total || 0;
            } catch (e) {
                this.fail(e);
            }
        },

        searchEvents() {
            this.eventQuery.pageNum = 1;
            this.loadEvents();
        },

        resetEventQuery() {
            this.eventQuery = {
                pageNum: 1, pageSize: 10, eventName: '',
                eventType: null, status: null, startDate: '', endDate: ''
            };
            this.loadEvents();
        },

        changeEventPage(step) {
            this.eventQuery.pageNum += step;
            this.loadEvents();
        },

        goEventPage(p) {
            this.eventQuery.pageNum = p;
            this.loadEvents();
        },

        sortBy(scope, key) {
            if (this.eventSort.key === key) {
                this.eventSort.dir = this.eventSort.dir === 'asc' ? 'desc' : 'asc';
            } else {
                this.eventSort.key = key;
                this.eventSort.dir = 'asc';
            }
        },

        openEventForm(row) {
            this.errors = {};
            this.form = row
                ? Object.assign({}, row)
                : { eventType: 1, status: 0, quota: 100, distanceKm: 42.195, entryFee: 200 };
            this.dialog = 'event';
        },

        /** 赛事详情抽屉，走 GET /api/event/detail/{id} */
        async viewEvent(id) {
            try {
                const d = await this.request('/api/event/detail/' + id);
                this.drawer = {
                    title: d.eventName,
                    sub: '赛事 #' + d.id,
                    rows: [
                        { label: '赛事类型', value: this.EVENT_TYPE[d.eventType] },
                        { label: '比赛日期', value: d.eventDate, mono: true },
                        { label: '起点地点', value: d.startLocation },
                        { label: '距离', value: d.distanceKm + ' km', mono: true },
                        { label: '总名额', value: d.quota, mono: true },
                        { label: '已报名', value: d.registeredCount, mono: true },
                        { label: '剩余名额', value: d.remainQuota, mono: true, cls: d.remainQuota <= 0 ? 'danger' : '' },
                        { label: '报名费', value: '¥' + d.entryFee, mono: true },
                        { label: '状态', value: this.EVENT_STATUS[d.status] },
                        { label: '创建时间', value: d.createTime, mono: true }
                    ],
                    extra: {
                        pct: this.quotaPercent(d),
                        text: '已报名 ' + d.registeredCount + ' 人，名额 ' + d.quota + ' 人'
                    },
                    action: { label: '查看报名名单', run: () => { this.closeDrawer(); this.goRegs(d.id); } }
                };
            } catch (e) {
                this.fail(e);
            }
        },

        delEvent(id) {
            this.ask('删除赛事', '确定删除该赛事？\n已有有效报名的赛事会被后端拒绝。', async () => {
                try {
                    await this.request('/api/event/delete/' + id, { method: 'DELETE' });
                    this.toast('已删除');
                    this.loadEvents();
                    this.loadAllEvents();
                } catch (e) {
                    this.fail(e);
                }
            });
        },

        exportEvents() {
            const rows = [['ID', '赛事名称', '类型', '日期', '起点', '距离(km)', '名额', '已报名', '剩余', '报名费', '状态']];
            this.sortedEvents.forEach(e => {
                rows.push([
                    e.id, e.eventName, this.EVENT_TYPE[e.eventType], e.eventDate,
                    e.startLocation, e.distanceKm, e.quota, e.registeredCount,
                    e.remainQuota, e.entryFee, this.EVENT_STATUS[e.status]
                ]);
            });
            this.downloadCsv('赛事列表_' + this.todayStr() + '.csv', rows);
        },

        /* ==================== 选手 ==================== */

        async loadRunners() {
            try {
                const data = await this.request('/api/runner/list?' + this.buildQuery(this.runnerQuery));
                this.runners = data.list || [];
                this.runnerPages = data.pages || 0;
                this.runnerTotal = data.total || 0;
            } catch (e) {
                this.fail(e);
            }
        },

        async loadAllRunners() {
            try {
                const data = await this.request('/api/runner/list?pageNum=1&pageSize=100');
                this.allRunners = data.list || [];
            } catch (e) {
                // 静默失败，不打扰用户
            }
        },

        searchRunners() {
            this.runnerQuery.pageNum = 1;
            this.loadRunners();
        },

        resetRunnerQuery() {
            this.runnerQuery = { pageNum: 1, pageSize: 10, name: '', phone: '', gender: null };
            this.loadRunners();
        },

        changeRunnerPage(step) {
            this.runnerQuery.pageNum += step;
            this.loadRunners();
        },

        goRunnerPage(p) {
            this.runnerQuery.pageNum = p;
            this.loadRunners();
        },

        openRunnerForm(row) {
            this.errors = {};
            this.form = row ? Object.assign({}, row) : { gender: 1 };
            this.dialog = 'runner';
        },

        /** 选手详情抽屉，走 GET /api/runner/detail/{id} */
        async viewRunner(id) {
            try {
                const d = await this.request('/api/runner/detail/' + id);
                this.drawer = {
                    title: d.name,
                    sub: '选手 #' + d.id,
                    rows: [
                        { label: '性别', value: d.gender === 1 ? '男' : '女' },
                        { label: '年龄', value: d.age + ' 岁', mono: true },
                        { label: '身份证号', value: this.maskId(d.idCard), mono: true },
                        { label: '手机号', value: this.maskPhone(d.phone), mono: true },
                        { label: '出生日期', value: d.birthDate, mono: true },
                        { label: '紧急联系人', value: d.emergencyContact },
                        { label: '建档时间', value: d.createTime, mono: true }
                    ],
                    action: { label: '编辑档案', run: () => { this.closeDrawer(); this.openRunnerForm(d); } }
                };
            } catch (e) {
                this.fail(e);
            }
        },

        delRunner(id) {
            this.ask('删除选手', '确定删除该选手？\n若有有效报名，需先取消报名。', async () => {
                try {
                    await this.request('/api/runner/delete/' + id, { method: 'DELETE' });
                    this.toast('已删除');
                    this.loadRunners();
                    this.loadAllRunners();
                } catch (e) {
                    this.fail(e);
                }
            });
        },

        exportRunners() {
            const rows = [['ID', '姓名', '性别', '年龄', '身份证号', '手机号', '紧急联系人']];
            this.runners.forEach(r => {
                rows.push([
                    r.id, r.name, r.gender === 1 ? '男' : '女', r.age,
                    r.idCard, r.phone, r.emergencyContact
                ]);
            });
            this.downloadCsv('选手名单_' + this.todayStr() + '.csv', rows);
        },

        /* ==================== 报名 ==================== */

        /** 统计未支付报名总数（只取 total，不拉明细），给侧栏红点用 */
        async loadUnpaidTotal() {
            try {
                const data = await this.request('/api/register/list?payStatus=0&status=1&pageNum=1&pageSize=1');
                this.unpaidTotal = data.total || 0;
            } catch (e) {
                // 静默失败，红点不显示即可
            }
        },

        async loadRegs() {
            try {
                const data = await this.request('/api/register/list?' + this.buildQuery(this.regQuery));
                this.regs = data.list || [];
                this.regPages = data.pages || 0;
                this.regTotal = data.total || 0;
                // 翻页/筛选后清掉已不在当前页的勾选，避免批量操作误伤别的记录
                const ids = this.regs.map(r => r.id);
                this.selectedRegIds = this.selectedRegIds.filter(id => ids.indexOf(id) >= 0);
            } catch (e) {
                this.fail(e);
            }
            this.loadUnpaidTotal();
        },

        searchRegs() {
            this.regQuery.pageNum = 1;
            this.loadRegs();
        },

        resetRegQuery() {
            this.regQuery = { pageNum: 1, pageSize: 10, eventId: null, name: '', payStatus: null, status: null };
            this.loadRegs();
        },

        changeRegPage(step) {
            this.regQuery.pageNum += step;
            this.loadRegs();
        },

        goRegPage(p) {
            this.regQuery.pageNum = p;
            this.loadRegs();
        },

        goRegs(eventId) {
            this.regQuery.eventId = eventId;
            this.regQuery.pageNum = 1;
            this.active = 'registration';
            this.loadRegs();
            this.loadAllRunners();
        },

        isSelected(id) {
            return this.selectedRegIds.indexOf(id) >= 0;
        },

        toggleSelect(id) {
            const i = this.selectedRegIds.indexOf(id);
            if (i >= 0) this.selectedRegIds.splice(i, 1);
            else this.selectedRegIds.push(id);
        },

        toggleSelectAll() {
            if (this.allSelected) {
                this.selectedRegIds = [];
            } else {
                this.selectedRegIds = this.regs.map(r => r.id);
            }
        },

        clearSelection() {
            this.selectedRegIds = [];
        },

        /** 批量确认支付 */
        batchMarkPaid() {
            const targets = this.regs.filter(r => this.isSelected(r.id) && r.payStatus !== 1 && r.status === 1);
            if (!targets.length) {
                this.toast('选中的记录都已支付或无效', 'info');
                return;
            }
            this.ask('批量确认支付', '将把 ' + targets.length + ' 条报名记录标记为已支付。', async () => {
                this.bulkBusy = true;
                let ok = 0, failCount = 0;
                for (const r of targets) {
                    try {
                        await this.request('/api/register/pay?id=' + r.id, { method: 'PUT' });
                        ok++;
                    } catch (e) {
                        failCount++;
                    }
                }
                this.bulkBusy = false;
                this.clearSelection();
                await this.loadRegs();
                if (failCount) {
                    this.toast('成功 ' + ok + ' 条，失败 ' + failCount + ' 条', 'err');
                } else {
                    this.toast('已确认支付 ' + ok + ' 条');
                }
            });
        },

        /** 批量分配号码布：逐条调 suggestBib 取建议值再写入 */
        batchAssignBib() {
            const targets = this.regs.filter(r => this.isSelected(r.id) && r.status === 1 && !r.bibNumber);
            if (!targets.length) {
                this.toast('选中的记录都已有号码布', 'info');
                return;
            }
            this.ask('批量分配号码布', '将为 ' + targets.length + ' 条记录按规则自动生成号码布。', async () => {
                this.bulkBusy = true;
                let ok = 0, failCount = 0;
                for (const r of targets) {
                    try {
                        const sug = await this.request('/api/register/suggestBib?id=' + r.id);
                        await this.request('/api/register/bib?id=' + r.id + '&bibNumber=' + encodeURIComponent(sug.bibNumber), { method: 'PUT' });
                        ok++;
                    } catch (e) {
                        failCount++;
                    }
                }
                this.bulkBusy = false;
                this.clearSelection();
                await this.loadRegs();
                if (failCount) {
                    this.toast('成功 ' + ok + ' 条，失败 ' + failCount + ' 条', 'err');
                } else {
                    this.toast('已分配号码布 ' + ok + ' 条');
                }
            });
        },

        openSubmit() {
            this.runnerPick = '';
            this.errors = {};
            this.form = { eventId: this.regQuery.eventId, runnerId: null };
            this.dialog = 'submit';
        },

        exportRegs() {
            const rows = [['报名ID', '号码布', '选手', '性别', '手机号', '赛事', '报名时间', '支付状态', '状态']];
            this.regs.forEach(r => {
                rows.push([
                    r.id, r.bibNumber || '',
                    r.runner ? r.runner.name : '',
                    r.runner ? (r.runner.gender === 1 ? '男' : '女') : '',
                    r.runner ? r.runner.phone : '',
                    r.event ? r.event.eventName : '',
                    r.registerTime,
                    r.payStatus === 1 ? '已支付' : '未支付',
                    this.REG_STATUS[r.status] || ''
                ]);
            });
            this.downloadCsv('报名名单_' + this.todayStr() + '.csv', rows);
        },

        async doSubmit() {
            this.errors = {};
            if (!this.form.eventId) {
                this.errors.eventId = '请选择赛事';
            }
            if (!this.form.runnerId) {
                this.errors.runnerId = '请选择选手';
            }
            if (Object.keys(this.errors).length) return;

            this.saving = true;
            try {
                await this.request('/api/register/submit', { method: 'POST', body: this.form });
                this.toast('报名成功');
                this.closeDialog();
                await this.loadRegs();
                this.loadEvents();
                this.loadAllEvents();
            } catch (e) {
                this.fail(e);
            } finally {
                this.saving = false;
            }
        },

        cancelReg(id) {
            this.ask('取消报名', '报名记录会被物理删除，号码布一并释放，报名人数减 1。\n已录入成绩的记录不允许取消。', async () => {
                try {
                    await this.request('/api/register/cancel?id=' + id, { method: 'POST' });
                    this.toast('报名已取消');
                    await this.loadRegs();
                    this.loadEvents();
                    this.loadAllEvents();
                } catch (e) {
                    this.fail(e);
                }
            });
        },

        markPaid(reg) {
            this.ask('确认支付', '将报名 #' + reg.id + ' 标记为已支付。', async () => {
                try {
                    await this.request('/api/register/pay?id=' + reg.id, { method: 'PUT' });
                    this.toast('已确认支付');
                    this.loadRegs();
                } catch (e) {
                    this.fail(e);
                }
            });
        },

        clearBib(reg) {
            this.ask('清空号码布', '清空号码布 ' + reg.bibNumber + ' ？\n报名记录保留，可重新分配。', async () => {
                try {
                    await this.request('/api/register/bib?id=' + reg.id, { method: 'DELETE' });
                    this.toast('号码布已清空');
                    this.loadRegs();
                } catch (e) {
                    this.fail(e);
                }
            });
        },

        async openBibForm(reg) {
            this.errors = {};
            this.form = { registrationId: reg.id, bibNumber: reg.bibNumber || '' };
            this.dialog = 'bib';
            if (!this.form.bibNumber) {
                await this.fillSuggestBib();
            }
        },

        async fillSuggestBib() {
            try {
                const data = await this.request('/api/register/suggestBib?id=' + this.form.registrationId);
                this.form.bibNumber = data.bibNumber;
            } catch (e) {
                this.fail(e);
            }
        },

        async doBib() {
            this.errors = {};
            const v = (this.form.bibNumber || '').trim();
            if (!v) {
                this.errors.bibNumber = '号码布不能为空';
                return;
            }
            if (!/^[ABN]\d{3,4}$/.test(v)) {
                this.errors.bibNumber = '格式应为一位字母（A/B/N）+ 三到四位数字，如 A0005';
                return;
            }
            this.saving = true;
            try {
                const url = '/api/register/bib?id=' + this.form.registrationId
                    + '&bibNumber=' + encodeURIComponent(v);
                await this.request(url, { method: 'PUT' });
                this.toast('号码布已分配');
                this.closeDialog();
                this.loadRegs();
            } catch (e) {
                this.fail(e);
            } finally {
                this.saving = false;
            }
        },

        /* ==================== 成绩 ==================== */

        /**
         * 找报名记录所属赛事的当前状态。
         * 优先用行数据自带的 eventStatus（成绩明细 VO 里已带上），
         * 退回用 allEvents 缓存按 eventId 查。
         * 两条路都拿不到就返回 null（交给后端裁决，前端不拦）。
         */
        eventStatusOf(registrationId, explicitStatus) {
            if (explicitStatus != null) return explicitStatus;
            const reg = (this.regs || []).find(r => r.id === registrationId);
            if (!reg || reg.eventId == null) return null;
            const ev = (this.allEvents || []).find(e => e.id === reg.eventId);
            return ev ? ev.status : null;
        },

        /** 成绩录入前置校验：赛事必须是「比赛中」 */
        checkResultEventState(registrationId, explicitStatus) {
            const status = this.eventStatusOf(registrationId, explicitStatus);
            if (status === null) return true; // 状态未知，放行给后端判断
            if (status === 2) return true;
            if (status === 0) {
                this.toast('该赛事还在报名中，请先截止报名并开赛后才能录入成绩', 'info');
                return false;
            }
            if (status === 1) {
                this.toast('该赛事尚未开赛，请先把状态推进到「比赛中」', 'info');
                return false;
            }
            if (status === 3) {
                this.toast('赛事已结束，成绩已锁定，不允许再录入', 'info');
                return false;
            }
            return true;
        },

        inputResult(reg) {
            if (!this.checkResultEventState(reg.id, reg.eventStatus)) return;
            this.errors = {};
            this.form = {
                registrationId: reg.id,
                resultId: null,
                chipTimeStr: '',
                netTimeStr: '',
                finishStatus: 1,
                finishTime: ''
            };
            this.dialog = 'result';
        },

        /** 修改已有成绩：走 PUT /api/result/update */
        editResult(r) {
            if (!this.checkResultEventState(r.registrationId, r.eventStatus)) return;
            this.errors = {};
            this.form = {
                registrationId: r.registrationId,
                resultId: r.id,
                chipTimeStr: this.formatSeconds(r.chipTime),
                netTimeStr: this.formatSeconds(r.netTime),
                finishStatus: r.finishStatus,
                finishTime: r.finishTime ? r.finishTime.replace(' ', 'T').slice(0, 16) : ''
            };
            this.dialog = 'result';
        },

        async doResult() {
            this.errors = {};
            let chip, net;
            try {
                chip = this.parseHms(this.form.chipTimeStr);
                net = this.parseHms(this.form.netTimeStr);
            } catch (e) {
                const field = e.field || 'netTimeStr';
                this.errors[field] = e.message;
                return;
            }
            if (net > chip) {
                this.errors.netTimeStr = '净成绩不能大于枪声成绩';
                return;
            }

            const body = {
                registrationId: this.form.registrationId,
                chipTime: chip,
                netTime: net,
                finishStatus: this.form.finishStatus,
                finishTime: this.form.finishTime ? this.form.finishTime.replace('T', ' ') : null
            };
            if (body.finishTime && body.finishTime.length === 16) body.finishTime += ':00';

            this.saving = true;
            try {
                if (this.form.resultId) {
                    body.id = this.form.resultId;
                    await this.request('/api/result/update', { method: 'PUT', body: body });
                    this.toast('成绩已更新');
                } else {
                    await this.request('/api/result/input', { method: 'POST', body: body });
                    this.toast('成绩已录入');
                }
                this.closeDialog();
                await this.loadRankings();
                await this.loadResultRecords();
            } catch (e) {
                this.fail(e);
            } finally {
                this.saving = false;
            }
        },

        /* ==================== 选手登录密码 ==================== */

        /**
         * 打开「重置选手登录密码」弹窗。
         * <p>
         * 解决真实业务缺口：管理员在 B 端建的选手档案，
         * 选手在 C 端注册会被「身份证已建过档案」挡掉，登录又没有账号 ——
         * 只能靠管理员在这里开通/重置。
         */
        async openResetPwd(r) {
            this.errors = {};
            this.resetPwdInfo = {
                runnerId: r.id,
                exists: false,
                phone: r.phone || '',
                runnerName: r.name || ''
            };
            try {
                const st = await this.request('/api/member-account/status/' + r.id);
                this.resetPwdInfo = {
                    runnerId: r.id,
                    exists: !!st.exists,
                    phone: st.phone || r.phone || '',
                    runnerName: st.runnerName || r.name || ''
                };
            } catch (e) {
                // 状态查不到不阻断，先用列表里的数据兜底
            }
            this.form = { resetPassword: '' };
            this.dialog = 'resetPwd';
        },

        async doResetPwd() {
            this.errors = {};
            const pwd = (this.form.resetPassword || '').trim();
            if (!pwd) {
                this.errors.resetPassword = '密码不能为空';
                return;
            }
            if (pwd.length < 6) {
                this.errors.resetPassword = '密码至少 6 位';
                return;
            }
            if (pwd.length > 30) {
                this.errors.resetPassword = '密码不能超过 30 位';
                return;
            }
            const runnerId = this.resetPwdInfo.runnerId;
            this.saving = true;
            try {
                const res = await this.request('/api/member-account/reset/' + runnerId, {
                    method: 'POST',
                    body: { password: pwd }
                });
                this.toast(res.created ? '已为该选手开通 C 端账号' : '密码已重置');
                this.closeDialog();
            } catch (e) {
                this.fail(e);
            } finally {
                this.saving = false;
            }
        },

        async loadRankings() {
            if (this.rankEventId === null || this.rankEventId === undefined) return;
            const id = this.rankEventId;
            try {
                this.stats = await this.request('/api/statistics/eventOverview/' + id);
                const all = await this.request('/api/result/eventRanking/' + id + '?pageNum=1&pageSize=100');
                this.rankAll = all.list || [];
                const gender = await this.request('/api/result/genderRanking/' + id);
                this.rankMale = gender.maleList || [];
                this.rankFemale = gender.femaleList || [];
                const age = await this.request('/api/result/ageRanking/' + id);
                this.ageGroups = [
                    { key: 'g1', label: '≤29 岁', list: age.group_29below || [] },
                    { key: 'g2', label: '30-39 岁', list: age.group_30_39 || [] },
                    { key: 'g3', label: '40-49 岁', list: age.group_40_49 || [] },
                    { key: 'g4', label: '≥50 岁', list: age.group_50up || [] }
                ];
                if (this.rankTab === 'records') await this.loadResultRecords();
            } catch (e) {
                this.fail(e);
            }
        },

        async loadResultRecords() {
            if (!this.rankEventId) return;
            try {
                const data = await this.request('/api/result/list?eventId=' + this.rankEventId
                    + '&pageNum=' + this.resultQuery.pageNum + '&pageSize=' + this.resultQuery.pageSize);
                this.resultRecords = data.list || [];
                this.resultPages = data.pages || 0;
                this.resultTotal = data.total || 0;
            } catch (e) {
                this.fail(e);
            }
        },

        changeResultPage(step) {
            this.resultQuery.pageNum += step;
            this.loadResultRecords();
        },

        goResultPage(p) {
            this.resultQuery.pageNum = p;
            this.loadResultRecords();
        },

        switchRankTab(tab) {
            this.rankTab = tab;
            if (tab === 'records') this.loadResultRecords();
        },

        printRanking() {
            if (!this.stats) {
                this.toast('请先选择赛事', 'info');
                return;
            }
            window.print();
        },

        /* ==================== 现场检录 ==================== */

        async loadCheckinProgress() {
            if (!this.checkinEventId) return;
            try {
                const d = await this.request('/api/checkin/progress/' + this.checkinEventId);
                this.checkinProgress = d;
            } catch (e) {
                this.fail(e);
            }
        },

        /** 核销检录：号码布或报名 ID 都行 */
        async doCheckin() {
            const raw = (this.checkinInput || '').trim();
            if (!raw) return;

            // 纯数字当报名 ID，否则当号码布
            const isId = /^\d+$/.test(raw);
            const url = isId
                ? '/api/checkin?id=' + raw
                : '/api/checkin?bibNumber=' + encodeURIComponent(raw);

            this.saving = true;
            try {
                const d = await this.request(url, { method: 'POST' });
                this.checkinResult = d;
                this.checkinInput = '';
                if (d.alreadyCheckedIn) {
                    this.toast('该选手已检录过', 'info');
                } else {
                    this.toast('检录成功：' + (d.name || '') + ' ' + (d.bibNumber || ''));
                }
                await this.loadCheckinProgress();
            } catch (e) {
                this.checkinResult = null;
                this.fail(e);
            } finally {
                this.saving = false;
            }
        },

        /* ==================== 证书签发 ==================== */

        async loadCertList() {
            if (!this.certEventId) return;
            try {
                const list = await this.request('/api/certificate/list/' + this.certEventId);
                this.certList = list || [];
            } catch (e) {
                this.fail(e);
            }
        },

        async issueCerts() {
            if (!this.certEventId) return;
            const pending = this.certList.filter(c => !c.certIssued).length;
            if (!pending) {
                this.toast('没有待签发的证书', 'info');
                return;
            }
            this.ask('批量签发证书', '将为 ' + pending + ' 位完赛选手签发电子证书。', async () => {
                try {
                    const d = await this.request('/api/certificate/issue?eventId=' + this.certEventId, { method: 'POST' });
                    this.toast('已签发 ' + d.count + ' 份证书');
                    await this.loadCertList();
                } catch (e) {
                    this.fail(e);
                }
            });
        },

        viewCert(c) {
            this.certPreview = c;
        },

        printCertPreview() {
            window.print();
        },

        exportCerts() {
            const rows = [['姓名', '号码布', '净成绩', '枪声成绩', '完赛时间', '证书签发时间']];
            this.certList.forEach(c => {
                rows.push([
                    c.runnerName, c.bibNumber, c.netTimeStr,
                    this.formatSeconds(c.chipTime), c.finishTime || '',
                    c.certIssued || '待签发'
                ]);
            });
            this.downloadCsv('完赛证书名单_' + this.todayStr() + '.csv', rows);
        },

        /* ==================== 弹窗 ==================== */

        submitDialog() {
            if (this.dialog === 'submit') this.doSubmit();
            else if (this.dialog === 'bib') this.doBib();
            else if (this.dialog === 'result') this.doResult();
            else if (this.dialog === 'resetPwd') this.doResetPwd();
            else this.doSave();
        },

        closeDialog() {
            this.dialog = null;
            this.form = {};
            this.errors = {};
            this.runnerPick = '';
            this.saving = false;
        },

        closeDrawer() {
            this.drawer = null;
        },

        /** 赛事 / 选手保存，前端先做一轮格式校验 */
        async doSave() {
            const isEvent = this.dialog === 'event';
            const isEdit = !!this.form.id;
            this.errors = {};

            if (isEvent) {
                const name = (this.form.eventName || '').trim();
                if (!name) this.errors.eventName = '赛事名称不能为空';
                if (!this.form.eventDate) this.errors.eventDate = '比赛日期不能为空';
                if (!(this.form.startLocation || '').trim()) this.errors.startLocation = '起点地点不能为空';
                if (!this.form.distanceKm || Number(this.form.distanceKm) <= 0) this.errors.distanceKm = '距离必须大于 0';
                if (!this.form.quota || Number(this.form.quota) <= 0) this.errors.quota = '名额必须大于 0';
                if (this.form.entryFee === null || this.form.entryFee === undefined || Number(this.form.entryFee) < 0) {
                    this.errors.entryFee = '报名费不能为负数';
                }
            } else {
                const idCard = (this.form.idCard || '').trim();
                const phone = (this.form.phone || '').trim();
                if (!(this.form.name || '').trim()) this.errors.name = '姓名不能为空';
                if (!/^\d{17}[\dXx]$/.test(idCard)) this.errors.idCard = '身份证号必须是 18 位（末位可为 X）';
                if (!/^1[3-9]\d{9}$/.test(phone)) this.errors.phone = '手机号格式不正确';
                if (!this.form.birthDate) {
                    this.errors.birthDate = '出生日期不能为空';
                } else {
                    const age = this.calcAge(this.form.birthDate);
                    if (age < 16) this.errors.birthDate = '参赛年龄不得低于 16 周岁，当前 ' + age + ' 岁';
                }
                if (!(this.form.emergencyContact || '').trim()) this.errors.emergencyContact = '紧急联系人不能为空';
            }
            if (Object.keys(this.errors).length) return;

            const url = isEvent
                ? (isEdit ? '/api/event/update' : '/api/event/add')
                : (isEdit ? '/api/runner/update' : '/api/runner/add');

            this.saving = true;
            try {
                await this.request(url, {
                    method: isEdit ? 'PUT' : 'POST',
                    body: this.form
                });
                this.toast(isEdit ? '已更新' : (isEvent ? '赛事已创建' : '选手已建档'));
                this.closeDialog();
                await this.loadEvents();
                await this.loadAllEvents();
                await this.loadRunners();
                await this.loadAllRunners();
            } catch (e) {
                this.fail(e);
            } finally {
                this.saving = false;
            }
        },

        /* ==================== 工具方法 ==================== */

        calcAge(birth) {
            const b = new Date(birth);
            if (isNaN(b.getTime())) return 0;
            const now = new Date();
            let age = now.getFullYear() - b.getFullYear();
            const m = now.getMonth() - b.getMonth();
            if (m < 0 || (m === 0 && now.getDate() < b.getDate())) age--;
            return age;
        },

        maskId(v) {
            if (!v) return '';
            if (v.length < 8) return v;
            return v.slice(0, 4) + '**********' + v.slice(-4);
        },

        maskPhone(v) {
            if (!v) return '';
            if (v.length < 7) return v;
            return v.slice(0, 3) + '****' + v.slice(-4);
        },

        quotaPercent(e) {
            if (!e || !e.quota) return 0;
            return Math.min(100, Math.round((e.registeredCount || 0) * 100 / e.quota));
        },

        eventTypeTag(type) {
            return { 1: 'tag', 2: 'tag teal', 3: 'tag gold' }[type] || 'tag';
        },

        eventStatusBadge(status) {
            // 0报名中 ok(绿) / 1报名截止 warn(黄) / 2比赛中 info(蓝) / 3已结束 plain(灰)
            return { 0: 'ok', 1: 'warn', 2: 'info', 3: 'plain' }[status] || 'plain';
        },

        regStatusBadge(status) {
            return { 1: 'ok', 2: 'plain', 3: 'bad' }[status] || 'plain';
        },

        finishBadge(status) {
            return { 1: 'ok', 2: 'warn', 3: 'bad' }[status] || 'plain';
        },

        medalBadge(rank) {
            if (rank === 1) return 'gold';
            if (rank === 2) return 'plain';
            if (rank === 3) return 'teal';
            return 'plain';
        },

        formatSeconds(seconds) {
            if (seconds === null || seconds === undefined || seconds < 0) return '--:--:--';
            const h = Math.floor(seconds / 3600);
            const m = Math.floor((seconds % 3600) / 60);
            const s = seconds % 60;
            return [h, m, s].map(n => String(n).padStart(2, '0')).join(':');
        },

        parseHms(str) {
            const raw = String(str || '').trim();
            if (!raw) {
                const e = new Error('请填写成绩，格式如 03:29:10');
                e.field = 'netTimeStr';
                throw e;
            }
            const parts = raw.split(':');
            if (parts.length !== 3) {
                const e = new Error('成绩请用 时:分:秒，例如 03:29:10');
                e.field = 'netTimeStr';
                throw e;
            }
            const h = Number(parts[0]);
            const m = Number(parts[1]);
            const s = Number(parts[2]);
            if ([h, m, s].some(n => !Number.isFinite(n) || n < 0) || m > 59 || s > 59) {
                const e = new Error('成绩时间不合法');
                e.field = 'netTimeStr';
                throw e;
            }
            return h * 3600 + m * 60 + s;
        },

        todayStr() {
            const d = new Date();
            const p = n => String(n).padStart(2, '0');
            return d.getFullYear() + p(d.getMonth() + 1) + p(d.getDate());
        },

        /** 导出 CSV（带 BOM，Excel 打开中文不乱码） */
        downloadCsv(filename, rows) {
            if (!rows || rows.length <= 1) {
                this.toast('没有可导出的数据', 'info');
                return;
            }
            const csv = rows.map(row => {
                return row.map(cell => {
                    const s = cell === null || cell === undefined ? '' : String(cell);
                    return /[",\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
                }).join(',');
            }).join('\r\n');

            const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8;' });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = filename;
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            URL.revokeObjectURL(url);
            this.toast('已导出 ' + filename);
        }
    },

    watch: {
        sidebarCollapsed(v) {
            localStorage.setItem('sidebarCollapsed', v ? '1' : '0');
        }
    }
}).mount('#app');
