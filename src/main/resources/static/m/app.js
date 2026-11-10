/* ==========================================================================
   Stride C 端 · 移动端逻辑
   面向选手：浏览 → 报名 → 我的报名/号码布 → 成绩/证书
   公开接口免登录，报名与「我的」需登录
   ========================================================================== */
const { createApp } = Vue;

createApp({
    data() {
        return {
            logged: !!localStorage.getItem('m_token'),
            token: localStorage.getItem('m_token') || '',
            memberId: localStorage.getItem('m_memberId') || '',
            memberName: localStorage.getItem('m_name') || '',
            memberPhone: localStorage.getItem('m_phone') || '',

            isRegister: false,
            loginForm: { phone: '', password: '' },
            regForm: {
                phone: '', password: '', name: '', gender: 1,
                idCard: '', birthDate: '', emergencyContact: ''
            },
            loginError: '',
            errors: {},
            busy: false,

            view: 'events',
            stack: [],           // 页面栈，用于返回
            allEvents: [],
            filterType: null,
            loadingEvents: false,

            event: null,
            registerSheet: false,
            agreed: false,

            myRegs: [],
            loadingOrders: false,

            myResults: [],
            loadingResults: false,

            currentReg: null,
            cert: null,

            rankEventId: null,
            rankGroup: 'total',
            rankTotal: [],
            rankMale: [],
            rankFemale: [],
            ageGroups: [],

            profile: {},
            profileForm: {},

            // 修改密码
            pwdForm: { oldPassword: '', newPassword: '', confirmPassword: '' },
            pwdErrors: {},

            confirmBox: null,
            toasts: [],
            toastSeq: 0
        };
    },

    computed: {
        EVENT_TYPE() { return { 1: '全程马拉松', 2: '半程马拉松', 3: '迷你跑' }; },
        // 赛事状态机：0报名中 → 1报名截止 → 2比赛中 → 3已结束
        // 与后端 common/EventStatus.java 保持一致
        EVENT_STATUS() { return { 0: '报名中', 1: '报名截止', 2: '比赛中', 3: '已结束' }; },
        FINISH_STATUS() { return { 1: '完赛', 2: '未完赛', 3: '退赛' }; },

        memberInitial() {
            return (this.memberName || '选').slice(0, 1);
        },

        filteredEvents() {
            if (this.filterType === null) return this.allEvents;
            return this.allEvents.filter(e => e.eventType === this.filterType);
        },

        pageTitle() {
            const map = {
                events: '赛事广场',
                event: '赛事详情',
                orders: '我的报名',
                bib: '电子号码布',
                results: '我的成绩',
                cert: '完赛证书',
                rank: '排行榜',
                mine: '我的',
                profile: '个人资料'
            };
            return map[this.view] || 'Stride';
        },

        showBack() {
            return this.stack.length > 0;
        },

        finishedCount() {
            return this.myResults.filter(r => r.finishStatus === 1).length;
        },

        avgNetTimeStr() {
            const done = this.myResults.filter(r => r.finishStatus === 1 && r.netTime);
            if (!done.length) return '—';
            const avg = Math.round(done.reduce((s, r) => s + r.netTime, 0) / done.length);
            return this.formatSeconds(avg);
        }
    },

    mounted() {
        this.loadEvents();
        if (this.logged) {
            this.bootstrap();
        }
    },

    methods: {
        /* ==================== 网络 ==================== */

        async request(url, options) {
            const opt = options || {};
            const headers = { 'Content-Type': 'application/json; charset=UTF-8' };
            if (!opt.skipAuth && this.token) {
                headers['Authorization'] = 'Bearer ' + this.token;
            }
            const resp = await fetch(url, {
                method: opt.method || 'GET',
                headers: headers,
                body: opt.body ? JSON.stringify(opt.body) : undefined
            });
            let json;
            try {
                json = await resp.json();
            } catch (e) {
                throw new Error('服务器响应异常（HTTP ' + resp.status + '）');
            }
            if (json.code !== 200) {
                if (json.code === 40100 || json.code === 40102) {
                    this.clearLogin();
                    throw new Error('登录已过期，请重新登录');
                }
                throw new Error(json.msg || '请求失败');
            }
            return json.data;
        },

        /* ==================== 账号 ==================== */

        switchAuth(mode) {
            this.isRegister = mode === 'register';
            this.loginError = '';
            this.errors = {};
        },

        async doLogin() {
            this.loginError = '';
            this.errors = {};
            if (!/^1[3-9]\d{9}$/.test(this.loginForm.phone)) {
                this.errors.phone = '请输入正确的 11 位手机号';
                return;
            }
            if (!this.loginForm.password) {
                this.errors.password = '请输入密码';
                return;
            }
            this.busy = true;
            try {
                const data = await this.request('/api/member/login', {
                    method: 'POST',
                    body: this.loginForm,
                    skipAuth: true
                });
                this.afterLogin(data);
                this.toast('登录成功');
            } catch (e) {
                this.loginError = e.message;
            } finally {
                this.busy = false;
            }
        },

        async doRegister() {
            this.loginError = '';
            this.errors = {};
            const f = this.regForm;
            if (!/^1[3-9]\d{9}$/.test(f.phone)) this.errors.phone = '请输入正确的 11 位手机号';
            if (!f.password || f.password.length < 6) this.errors.password = '密码至少 6 位';
            if (!f.name) this.errors.name = '请填写姓名';
            if (!/^\d{17}[\dXx]$/.test(f.idCard)) this.errors.idCard = '身份证号必须是 18 位（末位可为 X）';
            if (!f.birthDate) this.errors.birthDate = '请选择出生日期';
            if (!f.emergencyContact) this.errors.emergencyContact = '请填写紧急联系人';
            if (Object.keys(this.errors).length) return;

            this.busy = true;
            try {
                const data = await this.request('/api/member/register', {
                    method: 'POST',
                    body: f,
                    skipAuth: true
                });
                this.afterLogin(data);
                this.toast('注册成功，欢迎加入');
            } catch (e) {
                this.loginError = e.message;
            } finally {
                this.busy = false;
            }
        },

        afterLogin(data) {
            this.token = data.token;
            this.memberId = data.runnerId;
            this.memberName = data.name;
            this.memberPhone = data.phone;
            localStorage.setItem('m_token', this.token);
            localStorage.setItem('m_memberId', data.runnerId);
            localStorage.setItem('m_name', data.name);
            localStorage.setItem('m_phone', data.phone);
            this.logged = true;
            this.bootstrap();
        },

        clearLogin() {
            localStorage.removeItem('m_token');
            localStorage.removeItem('m_memberId');
            localStorage.removeItem('m_name');
            localStorage.removeItem('m_phone');
            this.token = '';
            this.memberId = '';
            this.memberName = '';
            this.memberPhone = '';
            this.logged = false;
            this.myRegs = [];
            this.myResults = [];
            this.view = 'events';
            this.stack = [];
        },

        askLogout() {
            this.confirmBox = {
                title: '退出登录',
                text: '确定要退出当前账号吗？',
                danger: true,
                ok: () => this.doLogout()
            };
        },

        async doLogout() {
            try {
                await this.request('/api/member/logout', { method: 'POST' });
            } catch (e) {
                // 服务端已注销或不可达，本地状态照清
            }
            this.clearLogin();
            this.toast('已退出登录', 'info');
        },

        /* ==================== 导航 ==================== */

        switchView(v) {
            this.view = v;
            this.stack = [];
            if (v === 'orders') this.loadMyRegs();
            if (v === 'results') this.loadMyResults();
            if (v === 'rank') this.initRank();
        },

        push(v) {
            this.stack.push(this.view);
            this.view = v;
        },

        goBack() {
            const prev = this.stack.pop();
            this.view = prev || 'events';
            if (this.view === 'orders') this.loadMyRegs();
            if (this.view === 'results') this.loadMyResults();
        },

        reloadCurrent() {
            if (this.view === 'events') this.loadEvents();
            else if (this.view === 'orders') this.loadMyRegs();
            else if (this.view === 'results') this.loadMyResults();
            else if (this.view === 'rank') this.loadRank();
            else if (this.view === 'event') this.openEvent(this.event.id);
            this.toast('已刷新', 'info');
        },

        goOrders() {
            this.switchView('orders');
        },

        goResults() {
            this.switchView('results');
        },

        goAdmin() {
            window.location.href = '/';
        },

        /* ==================== 赛事 ==================== */

        async bootstrap() {
            await this.loadEvents();
            this.loadMyRegs();
            this.loadMyResults();
            this.loadProfile();
        },

        async loadEvents() {
            this.loadingEvents = true;
            try {
                const list = await this.request('/api/public/events', { skipAuth: !this.token });
                this.allEvents = list || [];
                if (this.rankEventId === null && this.allEvents.length) {
                    // 默认选报名人数最多的那场，榜单更可能有内容
                    const best = this.allEvents.slice().sort((a, b) =>
                        (b.registeredCount || 0) - (a.registeredCount || 0))[0];
                    this.rankEventId = best ? best.id : null;
                }
            } catch (e) {
                this.fail(e);
            } finally {
                this.loadingEvents = false;
            }
        },

        async openEvent(id) {
            try {
                const d = await this.request('/api/public/events/' + id, { skipAuth: !this.token });
                this.event = d;
                this.push('event');
            } catch (e) {
                this.fail(e);
            }
        },

        /* ==================== 报名 ==================== */

        openRegister() {
            if (!this.logged) {
                this.toast('请先登录后再报名', 'err');
                return;
            }
            this.agreed = false;
            this.loadProfile();
            this.registerSheet = true;
        },

        async doRegisterEvent() {
            if (!this.agreed) return;
            this.busy = true;
            try {
                await this.request('/api/member/events/' + this.event.id + '/register', {
                    method: 'POST',
                    body: {}
                });
                this.registerSheet = false;
                this.toast('报名成功');
                await this.loadEvents();
                await this.loadMyRegs();
                this.event.registered = true;
            } catch (e) {
                this.fail(e);
            } finally {
                this.busy = false;
            }
        },

        askCancel(reg) {
            this.confirmBox = {
                title: '取消报名',
                text: '确定取消「' + reg.eventName + '」的报名吗？\n取消后名额将释放，需重新报名。',
                danger: true,
                ok: () => this.doCancel(reg.id)
            };
        },

        async doCancel(id) {
            try {
                await this.request('/api/member/registrations/' + id + '/cancel', { method: 'POST' });
                this.toast('报名已取消');
                await this.loadMyRegs();
                await this.loadEvents();
            } catch (e) {
                this.fail(e);
            }
        },

        async loadMyRegs() {
            if (!this.logged) return;
            this.loadingOrders = true;
            try {
                const list = await this.request('/api/member/registrations');
                this.myRegs = list || [];
            } catch (e) {
                this.fail(e);
            } finally {
                this.loadingOrders = false;
            }
        },

        openBib(reg) {
            this.currentReg = reg;
            this.push('bib');
        },

        async shareBib() {
            if (!this.currentReg) return;
            const text = '我的号码布 ' + (this.currentReg.bibNumber || '待分配')
                + ' · ' + this.currentReg.eventName;
            try {
                if (navigator.share) {
                    await navigator.share({ title: '我的号码布', text: text });
                    return;
                }
                await navigator.clipboard.writeText(text);
                this.toast('号码布信息已复制');
            } catch (e) {
                this.toast('号码布：' + (this.currentReg.bibNumber || '待分配'), 'info');
            }
        },

        /* ==================== 成绩 ==================== */

        async loadMyResults() {
            if (!this.logged) return;
            this.loadingResults = true;
            try {
                const list = await this.request('/api/member/results');
                this.myResults = list || [];
            } catch (e) {
                this.fail(e);
            } finally {
                this.loadingResults = false;
            }
        },

        async openCert(reg) {
            try {
                const d = await this.request('/api/member/certificates/' + reg.myRegistrationId);
                this.cert = d;
                this.cert.runnerName = this.memberName;
                this.push('cert');
            } catch (e) {
                this.fail(e);
            }
        },

        async openCertByResult(r) {
            try {
                const d = await this.request('/api/member/certificates/' + r.registrationId);
                this.cert = d;
                this.cert.runnerName = this.memberName;
                this.push('cert');
            } catch (e) {
                this.fail(e);
            }
        },

        printCert() {
            window.print();
        },

        /* ==================== 排行榜 ==================== */

        initRank() {
            if (this.rankEventId === null) {
                // 默认选报名人数最多的那场，比「最新的一场」更可能有成绩可看
                const best = this.allEvents.slice().sort((a, b) =>
                    (b.registeredCount || 0) - (a.registeredCount || 0))[0];
                this.rankEventId = best ? best.id : null;
            }
            this.loadRank();
        },

        async loadRank() {
            if (!this.rankEventId) return;
            try {
                const d = await this.request('/api/public/events/' + this.rankEventId + '/rankings?group=' + this.rankGroup);
                this.rankTotal = d.list || [];
                this.rankMale = d.maleList || [];
                this.rankFemale = d.femaleList || [];
                this.ageGroups = [
                    { key: 'g1', label: '≤29 岁', list: d.group_29below || [] },
                    { key: 'g2', label: '30-39 岁', list: d.group_30_39 || [] },
                    { key: 'g3', label: '40-49 岁', list: d.group_40_49 || [] },
                    { key: 'g4', label: '≥50 岁', list: d.group_50up || [] }
                ];
            } catch (e) {
                this.fail(e);
            }
        },

        /* ==================== 资料 ==================== */

        async loadProfile() {
            if (!this.logged) return;
            try {
                const d = await this.request('/api/member/profile');
                this.profile = d;
                this.profileForm = {
                    name: d.name,
                    gender: d.gender,
                    idCard: d.idCard,
                    phone: d.phone,
                    birthDate: d.birthDate,
                    emergencyContact: d.emergencyContact
                };
                this.memberName = d.name;
                this.memberPhone = d.phone;
                localStorage.setItem('m_name', d.name);
                localStorage.setItem('m_phone', d.phone);
            } catch (e) {
                // 静默
            }
        },

        async openProfile() {
            await this.loadProfile();
            this.push('profile');
        },

        async saveProfile() {
            this.busy = true;
            try {
                await this.request('/api/member/profile', {
                    method: 'PUT',
                    body: {
                        name: this.profileForm.name,
                        gender: this.profileForm.gender,
                        emergencyContact: this.profileForm.emergencyContact
                    }
                });
                this.toast('已保存');
                await this.loadProfile();
                this.goBack();
            } catch (e) {
                this.fail(e);
            } finally {
                this.busy = false;
            }
        },

        /* ==================== 修改密码 ==================== */

        openPassword() {
            this.pwdForm = { oldPassword: '', newPassword: '', confirmPassword: '' };
            this.pwdErrors = {};
            this.push('password');
        },

        /** 前端校验，返回 true 表示可以提交 */
        validatePassword() {
            const errs = {};
            const f = this.pwdForm;
            if (!f.oldPassword) {
                errs.oldPassword = '请输入原密码';
            }
            if (!f.newPassword) {
                errs.newPassword = '请输入新密码';
            } else if (f.newPassword.length < 6) {
                errs.newPassword = '新密码至少 6 位';
            } else if (f.newPassword.length > 30) {
                errs.newPassword = '新密码不能超过 30 位';
            }
            if (!f.confirmPassword) {
                errs.confirmPassword = '请再次输入新密码';
            } else if (f.newPassword && f.confirmPassword !== f.newPassword) {
                errs.confirmPassword = '两次输入的密码不一致';
            }
            if (f.oldPassword && f.newPassword && f.oldPassword === f.newPassword) {
                errs.newPassword = '新密码不能与原密码相同';
            }
            this.pwdErrors = errs;
            return Object.keys(errs).length === 0;
        },

        async savePassword() {
            if (!this.validatePassword()) return;
            this.busy = true;
            try {
                await this.request('/api/member/password', {
                    method: 'PUT',
                    body: {
                        oldPassword: this.pwdForm.oldPassword,
                        newPassword: this.pwdForm.newPassword
                    }
                });
                this.toast('密码已修改');
                this.pwdForm = { oldPassword: '', newPassword: '', confirmPassword: '' };
                this.pwdErrors = {};
                this.goBack();
            } catch (e) {
                this.fail(e);
            } finally {
                this.busy = false;
            }
        },

        /* ==================== 工具 ==================== */

        quotaPercent(e) {
            if (!e || !e.quota) return 0;
            return Math.min(100, Math.round((e.registeredCount || 0) * 100 / e.quota));
        },

        eventTypeTag(type) {
            return { 1: 'tag', 2: 'tag blue', 3: 'tag gold' }[type] || 'tag gray';
        },

        eventStatusBadge(status) {
            // 0报名中 ok / 1报名截止 warn / 2比赛中 info / 3已结束 plain
            return { 0: 'ok', 1: 'warn', 2: 'info', 3: 'plain' }[status] || 'plain';
        },

        regPayBadge(pay) {
            return pay === 1 ? 'ok' : 'warn';
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

        formatSeconds(seconds) {
            if (seconds === null || seconds === undefined || seconds < 0) return '--:--:--';
            const h = Math.floor(seconds / 3600);
            const m = Math.floor((seconds % 3600) / 60);
            const s = seconds % 60;
            return [h, m, s].map(n => String(n).padStart(2, '0')).join(':');
        },

        toast(text, type) {
            const id = ++this.toastSeq;
            this.toasts.push({ id: id, text: text, type: type || 'ok' });
            setTimeout(() => {
                this.toasts = this.toasts.filter(t => t.id !== id);
            }, 2600);
        },

        fail(e) {
            this.toast(e && e.message ? e.message : String(e), 'err');
        },

        runConfirm() {
            const fn = this.confirmBox && this.confirmBox.ok;
            this.confirmBox = null;
            if (fn) fn();
        }
    },

    watch: {
        rankGroup() {
            this.loadRank();
        }
    }
}).mount('#app');
