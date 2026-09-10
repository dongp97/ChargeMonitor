/*!
 * 好日子 · 纯逻辑层（不碰 DOM，可在 Node 里直接测试）
 * 负责：数据规范化、日期推算、提醒生成、备份序列化
 */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) {
    module.exports = factory(require('./lunar.js'));
  } else {
    root.HRCore = factory(root.Lunar);
  }
})(typeof self !== 'undefined' ? self : this, function (L) {
  'use strict';

  var KINDS = {
    birthday: { label: '生日', ch: '生', c1: '#FF6B5B', c2: '#FF9068' },
    anniversary: { label: '纪念日', ch: '纪', c1: '#E8618C', c2: '#F5849B' },
    festival: { label: '节日', ch: '节', c1: '#F2A93B', c2: '#F7C05A' },
    countdown: { label: '倒数日', ch: '数', c1: '#4C9BE8', c2: '#6FC0F0' }
  };
  var KIND_ORDER = ['birthday', 'anniversary', 'festival', 'countdown'];
  var RELATIONS = ['家人', '亲戚', '朋友', '同事', '同学', '其他'];
  var REMIND_OPTS = [30, 15, 7, 3, 2, 1, 0];
  var SOLAR_MONTH_DAYS = [31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
  var HORIZON_DAYS = 120;
  var MAX_REMINDERS = 200;

  // 常用节日预设
  var PRESET_SOLAR = [
    { m: 1, d: 1, name: '元旦' }, { m: 2, d: 14, name: '情人节' },
    { m: 3, d: 8, name: '妇女节' }, { m: 5, d: 1, name: '劳动节' },
    { m: 6, d: 1, name: '儿童节' }, { m: 9, d: 10, name: '教师节' },
    { m: 10, d: 1, name: '国庆节' }, { m: 12, d: 25, name: '圣诞节' }
  ];
  var PRESET_LUNAR = [
    { m: 1, d: 1, name: '春节' }, { m: 1, d: 15, name: '元宵节' },
    { m: 5, d: 5, name: '端午节' }, { m: 7, d: 7, name: '七夕' },
    { m: 8, d: 15, name: '中秋节' }, { m: 9, d: 9, name: '重阳节' },
    { m: 12, d: 8, name: '腊八节' }, { m: 12, d: 30, name: '除夕' }
  ];

  // ---------------------------------------------------------------- 工具

  function uid() {
    return 'i' + Date.now().toString(36) + Math.random().toString(36).slice(2, 7);
  }

  function intOr(v, dflt) {
    var n = parseInt(v, 10);
    return isNaN(n) ? dflt : n;
  }

  function clamp(v, lo, hi, dflt) {
    var n = intOr(v, null);
    if (n == null) return dflt;
    if (n < lo) return lo;
    if (n > hi) return hi;
    return n;
  }

  function cleanText(s, max) {
    return String(s == null ? '' : s).replace(/[\u0000-\u001f\u007f]/g, '').trim().slice(0, max || 60);
  }

  // ---------------------------------------------------------------- 规范化

  function fixItem(raw) {
    var it = {};
    for (var k in raw) if (Object.prototype.hasOwnProperty.call(raw, k)) it[k] = raw[k];

    it.id = typeof it.id === 'string' && it.id ? it.id : uid();
    it.kind = KINDS[it.kind] ? it.kind : 'countdown';
    it.name = cleanText(it.name, 40) || '未命名';
    it.dateType = it.dateType === 'lunar' ? 'lunar' : 'solar';
    it.month = clamp(it.month, 1, 12, 1);
    it.day = clamp(it.day, 1, 31, 1);
    it.isLeap = it.isLeap === true;
    it.repeat = it.repeat === 'once' ? 'once' : 'year';

    var y = intOr(it.year, null);
    it.year = (y == null || y < 1900 || y > 2100) ? null : y;

    if (it.dateType === 'solar' && it.day > SOLAR_MONTH_DAYS[it.month - 1]) {
      it.day = SOLAR_MONTH_DAYS[it.month - 1];
    }
    if (it.repeat === 'once' && it.year == null) it.year = new Date().getFullYear();

    if (Array.isArray(it.remind)) {
      var seen = {};
      it.remind = it.remind
        .map(function (n) { return intOr(n, null); })
        .filter(function (n) { return n != null && n >= 0 && n <= 365 && !seen[n] && (seen[n] = 1); })
        .sort(function (a, b) { return b - a; });
    } else {
      it.remind = null;
    }

    it.relation = RELATIONS.indexOf(it.relation) >= 0 ? it.relation : '';
    it.note = cleanText(it.note, 200);
    it.pinned = it.pinned === true;
    it.createdAt = intOr(it.createdAt, 0) || Date.now();
    return it;
  }

  function defaultSettings(s) {
    s = s || {};
    var out = {};
    out.theme = (s.theme === 'light' || s.theme === 'dark') ? s.theme : 'system';
    out.defaultRemind = Array.isArray(s.defaultRemind) && s.defaultRemind.length
      ? s.defaultRemind.map(Number).filter(function (n) { return n >= 0 && n <= 365; })
      : [7, 1, 0];
    out.remindTime = /^\d{1,2}:\d{2}$/.test(s.remindTime || '') ? s.remindTime : '09:00';
    out.notifyOn = s.notifyOn !== false;
    return out;
  }

  function normalizeStore(obj) {
    obj = obj || {};
    var items = Array.isArray(obj.items) ? obj.items.map(fixItem) : [];
    // 去重（按 id 保留最后一个）
    var map = {}, order = [];
    items.forEach(function (it) {
      if (!map[it.id]) order.push(it.id);
      map[it.id] = it;
    });
    return {
      v: 1,
      items: order.map(function (id) { return map[id]; }),
      settings: defaultSettings(obj.settings)
    };
  }

  // ---------------------------------------------------------------- 推算

  /** 一条日程在今天的「下一次」信息 */
  function info(it, t) {
    t = (t == null) ? L.todayNum() : t;
    var n = L.nextOccurrence(it, t, it.repeat, true);
    var passed = false;

    if (n == null) {
      // 一次性的日子过期后不要从列表里消失 —— 否则用户会以为数据丢了。
      // 这里仍返回它的固定日期，由界面标注「已过去」。
      if (it.repeat !== 'once') return null;
      n = (it.dateType === 'lunar')
        ? L.numOfLunar(it.year, it.month, it.day, it.isLeap)
        : L.ymdToNum(it.year, it.month, it.day);
      if (n == null) return null;
      passed = n < t;
      if (!passed) return null;
    }

    var d = L.numToYmd(n);
    var lun = L.solarToLunar(d.y, d.m, d.d);
    var term = L.solarTerm(d.y, d.m, d.d);
    var age = null;
    if (it.year && it.kind === 'birthday') {
      age = (it.dateType === 'lunar' && lun) ? (lun.year - it.year) : (d.y - it.year);
      if (age < 0 || age > 140) age = null;
    }

    var pn = L.prevOccurrence(it, t, it.repeat);

    return {
      n: n,
      y: d.y, m: d.m, d: d.d,
      diff: n - t,
      passed: passed,
      wd: L.weekdayOfNum(n),
      lun: lun,
      term: term,
      age: age,
      prevNum: pn,
      sincePrev: pn == null ? null : (t - pn)
    };
  }

  /** 该日程的重复规则文案 */
  function ruleText(it) {
    if (it.dateType === 'lunar') {
      var t = '农历' + L.lunarText(it.month, it.day, it.isLeap);
      if (it.repeat === 'once') return (it.year ? it.year + '年 ' : '') + t;
      return '每年 ' + t;
    }
    if (it.repeat === 'once') {
      return (it.year ? it.year + '年 ' : '') + it.month + '月' + it.day + '日';
    }
    return '每年 ' + it.month + '月' + it.day + '日';
  }

  /** 列表副标题：下一次的公历日期 + 农历 + 年龄 */
  function subText(it, i) {
    if (!i) return '这条日子的日期无法推算';
    var s = i.m + '月' + i.d + '日 周' + L.WEEK_CN[i.wd];
    if (i.lun) s += ' · 农历' + L.lunarShort(i.lun);
    if (i.age != null && i.age > 0) s += ' · ' + i.age + ' 岁';
    return s;
  }

  /** 倒计时角标 */
  function diffText(diff) {
    if (diff === 0) return { main: '今天', txt: true, hot: true, sub: '' };
    if (diff === 1) return { main: '明天', txt: true, hot: true, sub: '' };
    if (diff === 2) return { main: '后天', txt: true, hot: false, sub: '' };
    if (diff > 0) return { main: String(diff), txt: false, hot: diff <= 7, sub: '天后' };
    return { main: String(-diff), txt: false, hot: false, sub: '天前' };
  }

  /** 带回溯的详情：已过去多少天 */
  function sinceText(n) {
    if (n == null) return '—';
    if (n === 0) return '就是今天';
    if (n === 1) return '昨天';
    if (n < 30) return n + ' 天前';
    if (n < 365) return Math.floor(n / 30) + ' 个月前';
    var y = Math.floor(n / 365), r = n - y * 365;
    return y + ' 年' + (r > 0 ? ' ' + Math.floor(r / 30) + ' 个月' : '') + '前';
  }

  // ---------------------------------------------------------------- 排序 / 分组

  function decorate(items, t) {
    var out = [];
    items.forEach(function (it) {
      var i = info(it, t);
      if (i) out.push({ it: it, i: i });
    });
    return out;
  }

  function sortByDate(rows) {
    rows.sort(function (a, b) {
      if (a.it.pinned !== b.it.pinned) return a.it.pinned ? -1 : 1;
      var ap = a.i.diff < 0 ? 1 : 0, bp = b.i.diff < 0 ? 1 : 0;
      if (ap !== bp) return ap - bp;          // 已过去的沉到末尾
      if (a.i.diff !== b.i.diff) return a.i.diff - b.i.diff;
      return a.it.name.localeCompare(b.it.name, 'zh');
    });
    return rows;
  }

  function sortByName(rows) {
    rows.sort(function (a, b) {
      return a.it.name.localeCompare(b.it.name, 'zh') || (a.i.diff - b.i.diff);
    });
    return rows;
  }

  function sortByCreated(rows) {
    rows.sort(function (a, b) { return b.it.createdAt - a.it.createdAt; });
    return rows;
  }

  // ---------------------------------------------------------------- 提醒

  function millisOfDay(dayNum, hhmm) {
    var ymd = L.numToYmd(dayNum);
    var p = String(hhmm || '09:00').split(':');
    var h = intOr(p[0], 9), mi = intOr(p[1], 0);
    if (h < 0 || h > 23) h = 9;
    if (mi < 0 || mi > 59) mi = 0;
    return new Date(ymd.y, ymd.m - 1, ymd.d, h, mi, 0, 0).getTime();
  }

  function notifTitle(it, i, d) {
    if (d === 0) {
      if (it.kind === 'birthday') return it.name + ' 生日快乐 🎂';
      if (it.kind === 'festival') return '今天是' + it.name;
      if (it.kind === 'countdown') return '今天就是「' + it.name + '」';
      return '今天是「' + it.name + '」';
    }
    if (it.kind === 'birthday') return it.name + ' 生日还有 ' + d + ' 天';
    return '「' + it.name + '」还有 ' + d + ' 天';
  }

  function notifText(it, i, d) {
    var s = i.m + '月' + i.d + '日 周' + L.WEEK_CN[i.wd];
    if (i.lun) s += ' · 农历' + L.lunarText(i.lun.month, i.lun.day, i.lun.isLeap);
    if (it.kind === 'birthday' && i.age) s += ' · 满 ' + i.age + ' 岁';
    var tail = '';
    if (it.kind === 'birthday') tail = d === 0 ? '记得说声生日快乐' : '该想想送什么了';
    else if (it.kind === 'anniversary') tail = d === 0 ? '纪念日快乐' : '快到纪念日了';
    else if (d === 0) tail = '';
    else tail = '别忘了';
    return s + (tail ? '｜' + tail : '');
  }

  /**
   * 生成未来一段时间内需要触发的提醒清单。
   * 每次数据变更 / 打开 App 都会全量重算并推给原生层。
   */
  function buildReminders(items, settings, t, now) {
    t = (t == null) ? L.todayNum() : t;
    now = (now == null) ? Date.now() : now;
    if (settings.notifyOn === false) return [];

    var out = [];
    items.forEach(function (it) {
      var i = info(it, t);
      if (!i) return;
      var days = (it.remind && it.remind.length) ? it.remind : settings.defaultRemind;
      if (!days || !days.length) return;
      days.forEach(function (d) {
        var trig = i.n - d;
        if (trig < t || trig > t + HORIZON_DAYS) return;
        var at = millisOfDay(trig, settings.remindTime);
        if (at <= now) return;
        out.push({
          id: it.id + '#' + d,
          title: notifTitle(it, i, d),
          text: notifText(it, i, d),
          at: at
        });
      });
    });
    out.sort(function (a, b) { return a.at - b.at; });
    return out.slice(0, MAX_REMINDERS);
  }

  // ---------------------------------------------------------------- 分享 / 备份

  function shareText(items, t) {
    t = (t == null) ? L.todayNum() : t;
    var rows = sortByDate(decorate(items, t));
    var today = L.numToYmd(t);
    var lines = [];
    lines.push('📅 好日子 · 我的重要日子');
    lines.push('（' + today.y + '年' + today.m + '月' + today.d + '日整理，共 ' + rows.length + ' 条）');
    lines.push('');

    var bucket = { 近: [], 中: [], 远: [], 过: [] };
    rows.forEach(function (r) {
      var key;
      if (r.i.diff < 0) key = '过';
      else if (r.i.diff <= 30) key = '近';
      else if (r.i.diff <= 180) key = '中';
      else key = '远';
      bucket[key].push(r);
    });

    var titles = { 近: '▍一个月内', 中: '▍半年内', 远: '▍更远', 过: '▍已经过去' };
    ['近', '中', '远', '过'].forEach(function (k) {
      if (!bucket[k].length) return;
      lines.push(titles[k]);
      bucket[k].forEach(function (r) {
        var d = diffText(r.i.diff);
        var when = d.txt ? d.main : (r.i.diff > 0 ? r.i.diff + ' 天后' : Math.abs(r.i.diff) + ' 天前');
        lines.push('· ' + r.it.name + '　' + r.i.m + '月' + r.i.d + '日　' + when +
          (r.it.dateType === 'lunar' && r.i.lun ? '　农历' + L.lunarText(r.i.lun.month, r.i.lun.day, r.i.lun.isLeap) : ''));
      });
      lines.push('');
    });

    if (!rows.length) lines.push('（还没有记录任何日子）');
    lines.push('—— 由「好日子」App 生成');
    return lines.join('\n');
  }

  function exportPayload(state) {
    return JSON.stringify({
      app: '好日子',
      v: 1,
      exportedAt: new Date().toISOString(),
      settings: state.settings,
      items: state.items
    }, null, 2);
  }

  function parseBackup(text) {
    var obj;
    try {
      obj = JSON.parse(text);
    } catch (e) {
      return { ok: false, msg: '不是有效的备份文件（JSON 解析失败）' };
    }
    if (!obj || !Array.isArray(obj.items)) {
      return { ok: false, msg: '备份文件里没有找到日子数据' };
    }
    var store = normalizeStore(obj);
    if (!store.items.length) {
      return { ok: false, msg: '备份文件是空的，没有可导入的内容' };
    }
    return { ok: true, store: store, count: store.items.length };
  }

  /** 合并：同 id 以新的为准 */
  function mergeItems(base, incoming) {
    var map = {}, order = [];
    base.concat(incoming).forEach(function (it) {
      if (!map[it.id]) order.push(it.id);
      map[it.id] = it;
    });
    return order.map(function (id) { return map[id]; });
  }

  // ---------------------------------------------------------------- 示例数据

  function presetItems(which, t) {
    t = (t == null) ? L.todayNum() : t;
    var out = [];
    if (which === 'lunar' || which === 'all') {
      PRESET_LUNAR.forEach(function (p) {
        out.push(fixItem({
          kind: 'festival', name: p.name, dateType: 'lunar',
          month: p.m, day: p.d, repeat: 'year', remind: p.name === '除夕' ? [1, 0] : [3, 0]
        }));
      });
    }
    if (which === 'solar' || which === 'all') {
      PRESET_SOLAR.forEach(function (p) {
        out.push(fixItem({
          kind: 'festival', name: p.name, dateType: 'solar',
          month: p.m, day: p.d, repeat: 'year', remind: [1, 0]
        }));
      });
    }
    return out;
  }

  function sampleItems() {
    var y = L.numToYmd(L.todayNum()).y;
    return [
      fixItem({
        kind: 'birthday', name: '妈妈', relation: '家人', dateType: 'lunar',
        month: 8, day: 15, year: y - 62, repeat: 'year', remind: [7, 3, 1, 0],
        note: '农历生日，记得提前订蛋糕'
      }),
      fixItem({
        kind: 'birthday', name: '老爸', relation: '家人', dateType: 'solar',
        month: 11, day: 3, year: y - 64, repeat: 'year', remind: [7, 1, 0]
      }),
      fixItem({
        kind: 'anniversary', name: '结婚纪念日', dateType: 'solar',
        month: 5, day: 20, year: y - 9, repeat: 'year', remind: [15, 7, 1, 0],
        note: '今年第九年'
      }),
      fixItem({
        kind: 'festival', name: '春节', dateType: 'lunar',
        month: 1, day: 1, repeat: 'year', remind: [15, 7, 1, 0]
      }),
      fixItem({
        kind: 'countdown', name: '年假出发', dateType: 'solar',
        month: Math.min(12, L.numToYmd(L.todayNum()).m + 2), day: 12,
        year: y, repeat: 'once', remind: [30, 7, 1, 0], note: '机票已订'
      }),
      fixItem({
        kind: 'birthday', name: '小侄女', relation: '亲戚', dateType: 'lunar',
        month: 6, day: 1, year: y - 7, repeat: 'year', remind: [3, 1, 0]
      })
    ];
  }

  // ---------------------------------------------------------------- 校验

  /** 闰月开关只有在目标年份真的存在该闰月时才有意义 */
  function hasLeapNear(month, fromYear, span) {
    span = span || 10;
    for (var y = fromYear; y < fromYear + span; y++) {
      if (L.leapMonth(y) === month) return true;
    }
    return false;
  }

  /** 农历日是否会在小月被顺延到月末 */
  function lunarDayMayShift(month, day) {
    if (day < 30) return false;
    for (var y = 2026; y <= 2060; y++) {
      if (L.monthDays(y, month) < day) return true;
    }
    return false;
  }

  function solarMonthDays(m) { return SOLAR_MONTH_DAYS[m - 1]; }

  return {
    KINDS: KINDS,
    KIND_ORDER: KIND_ORDER,
    RELATIONS: RELATIONS,
    REMIND_OPTS: REMIND_OPTS,
    PRESET_SOLAR: PRESET_SOLAR,
    PRESET_LUNAR: PRESET_LUNAR,
    HORIZON_DAYS: HORIZON_DAYS,
    MAX_REMINDERS: MAX_REMINDERS,

    uid: uid,
    fixItem: fixItem,
    defaultSettings: defaultSettings,
    normalizeStore: normalizeStore,

    info: info,
    ruleText: ruleText,
    subText: subText,
    diffText: diffText,
    sinceText: sinceText,
    decorate: decorate,
    sortByDate: sortByDate,
    sortByName: sortByName,
    sortByCreated: sortByCreated,

    millisOfDay: millisOfDay,
    notifTitle: notifTitle,
    notifText: notifText,
    buildReminders: buildReminders,

    shareText: shareText,
    exportPayload: exportPayload,
    parseBackup: parseBackup,
    mergeItems: mergeItems,

    presetItems: presetItems,
    sampleItems: sampleItems,
    hasLeapNear: hasLeapNear,
    lunarDayMayShift: lunarDayMayShift,
    solarMonthDays: solarMonthDays
  };
});
