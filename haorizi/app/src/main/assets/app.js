/*!
 * 好日子 · 界面层
 */
(function () {
  'use strict';

  var L = window.Lunar;
  var C = window.HRCore;
  var STORE_KEY = 'haorizi.v1';

  var native = null;
  try { native = (typeof AndroidBridge !== 'undefined') ? AndroidBridge : null; } catch (e) { native = null; }

  var S = C.normalizeStore(readRaw());
  var view = 'home';
  var calY = 0, calM = 0, calSel = null;
  var filterKind = 'all';
  var sortMode = 'date';
  var query = '';
  var draft = null;
  var sheetOpen = false;
  var tabRects = null;

  // ---------------------------------------------------------------- 存储

  function readRaw() {
    try {
      var raw = localStorage.getItem(STORE_KEY);
      return raw ? JSON.parse(raw) : {};
    } catch (e) { return {}; }
  }

  function persist() {
    try { localStorage.setItem(STORE_KEY, JSON.stringify(S)); } catch (e) { }
    syncReminders();
  }

  function syncReminders() {
    var list = C.buildReminders(S.items, S.settings);
    try { if (native) native.syncReminders(JSON.stringify(list)); } catch (e) { }
  }

  // ---------------------------------------------------------------- 原生桥

  function nVib() { try { if (native) native.vibrate(12); } catch (e) { } }
  function nChrome(bg, light) { try { if (native) native.setChrome(bg, light); } catch (e) { } }
  function nBack(v) { try { if (native) native.setBackHandled(!!v); } catch (e) { } }
  function nInfo() {
    try { return native ? JSON.parse(native.info()) : null; } catch (e) { return null; }
  }

  // ---------------------------------------------------------------- 小工具

  function esc(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  function $(id) { return document.getElementById(id); }

  var toastTimer = null;
  function toast(msg) {
    var el = $('toast');
    el.textContent = msg;
    el.classList.add('on');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { el.classList.remove('on'); }, 2100);
  }

  function isDark() {
    if (S.settings.theme === 'dark') return true;
    if (S.settings.theme === 'light') return false;
    return !!(window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches);
  }

  function applyTheme() {
    var dark = isDark();
    document.documentElement.setAttribute('data-theme', dark ? 'dark' : 'light');
    nChrome(dark ? '#141216' : '#FBF7F2', !dark);
  }

  function findItem(id) {
    for (var k = 0; k < S.items.length; k++) if (S.items[k].id === id) return S.items[k];
    return null;
  }

  function todayInfo() {
    var t = L.todayNum();
    var y = L.numToYmd(t);
    var lun = L.solarToLunar(y.y, y.m, y.d);
    return { t: t, y: y, lun: lun, wd: L.weekdayOfNum(t), term: L.solarTerm(y.y, y.m, y.d) };
  }

  // ---------------------------------------------------------------- 图标

  var ICON = {
    home: '<path d="M12 3.6v8.2l3 1.8"/><circle cx="12" cy="12" r="8.4"/>',
    list: '<path d="M4 7h11M4 12h16M4 17h8"/>',
    cal: '<rect x="3.4" y="5.2" width="17.2" height="15.2" rx="3"/><path d="M3.4 9.8h17.2M8 3.2v4M16 3.2v4"/>',
    gear: '<path d="M3 8h8M16.5 8H21M3 16h4.5M13 16H21"/><circle cx="13.5" cy="8" r="2.6"/><circle cx="10" cy="16" r="2.6"/>',
    plus: '<path d="M12 5.5v13M5.5 12h13"/>',
    search: '<circle cx="11" cy="11" r="6.4"/><path d="M15.8 15.8l4 4"/>',
    left: '<path d="M14.5 5.5L8 12l6.5 6.5"/>',
    right: '<path d="M9.5 5.5L16 12l-6.5 6.5"/>',
    close: '<path d="M6 6l12 12M18 6L6 18"/>'
  };

  function svg(name, size) {
    size = size || 22;
    return '<svg viewBox="0 0 24 24" width="' + size + '" height="' + size + '" fill="none" ' +
      'stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">' +
      ICON[name] + '</svg>';
  }

  // ---------------------------------------------------------------- 渲染骨架

  function render() {
    var page = $('page');
    page.innerHTML = V[view]();
    page.classList.remove('fade');
    void page.offsetWidth;
    page.classList.add('fade');
    $('tabbar').innerHTML = tabbar();
    nBack(sheetOpen || view !== 'home');
  }

  function tabbar() {
    var tabs = [
      { k: 'home', n: '近期', i: 'home' },
      { k: 'all', n: '全部', i: 'list' },
      { k: 'cal', n: '日历', i: 'cal' },
      { k: 'me', n: '设置', i: 'gear' }
    ];
    return tabs.map(function (t) {
      return '<button class="tab' + (view === t.k ? ' on' : '') + '" data-act="tab" data-id="' + t.k + '">' +
        svg(t.i) + '<span>' + t.n + '</span></button>';
    }).join('');
  }

  // ---------------------------------------------------------------- 首页

  var V = {};

  V.home = function () {
    var ti = todayInfo();
    var all = C.decorate(S.items, ti.t);
    // 主卡片永远给「真正最近」的那条 —— 否则「最近」两个字会说谎。
    // 置顶只影响下面列表的排序，不抢主卡片。
    var future = all.filter(function (r) { return r.i.diff >= 0; });
    future.sort(function (a, b) {
      return (a.i.diff - b.i.diff) || a.it.name.localeCompare(b.it.name, 'zh');
    });
    var past = C.sortByDate(all.filter(function (r) { return r.i.diff < 0; }));

    var head = headerHtml(ti);

    if (!S.items.length) return head + emptyHtml();
    if (!future.length) {
      return head +
        '<div class="sec"><h2>都过去了</h2><span>共 ' + past.length + ' 条</span></div>' +
        '<div class="list">' + past.map(itemHtml).join('') + '</div>' +
        '<div style="margin-top:18px"><button class="btn primary" data-act="add">添加新的日子</button></div>';
    }

    var hero = future[0];
    var rest = C.sortByDate(future.slice(1));
    var soon = rest.filter(function (r) { return r.i.diff <= 90; });
    var later = rest.filter(function (r) { return r.i.diff > 90; });

    var out = head + heroHtml(hero);

    out += '<div class="sec"><h2>接下来 90 天</h2><span>' + soon.length + ' 条</span></div>';
    out += soon.length
      ? '<div class="list">' + soon.map(itemHtml).join('') + '</div>'
      : '<div class="grp-h" style="margin:2px 4px 0">这三个月暂时没有别的日子</div>';

    if (later.length) {
      out += '<div class="sec"><h2>更远</h2><span>' + later.length + ' 条</span></div>';
      out += '<div class="list">' + later.map(itemHtml).join('') + '</div>';
    }
    if (past.length) {
      out += '<div class="sec"><h2>已经过去</h2><span>' + past.length + ' 条</span></div>';
      out += '<div class="list">' + past.map(itemHtml).join('') + '</div>';
    }
    return out;
  };

  function headerHtml(ti) {
    var sub = [];
    if (ti.lun) {
      sub.push('农历' + L.lunarText(ti.lun.month, ti.lun.day, ti.lun.isLeap));
      sub.push(L.ganzhiYear(ti.lun.year) + L.animalOf(ti.lun.year) + '年');
    }
    if (ti.term) sub.push(ti.term);
    sub.push(L.constellation(ti.y.m, ti.y.d));

    // 临时内部标记：只在本机原生桥给了字段时显示，用来一眼确认
    // 「装的是哪一版」+「原生留白到底算出多少」。修好后删掉这一段。
    var _di = nInfo();
    var diag = (_di && _di.safeTop !== undefined)
      ? '<div class="hd-sub" style="opacity:.55">内部标记 v' + esc(_di.version || '?') +
        ' · 顶部留白 ' + _di.safeTop + 'px · 底部 ' + _di.safeBottom + 'px</div>'
      : '';

    return '<div class="hd">' +
      '<div>' +
      '<div class="hd-day">' + ti.y.m + '月' + ti.y.d + '日' +
      '<span style="font-size:14px;color:var(--text-3);font-weight:500;margin-left:7px">周' +
      L.WEEK_CN[ti.wd] + '</span></div>' +
      '<div class="hd-sub">' + esc(sub.join(' · ')) + '</div>' +
      diag +
      '</div>' +
      '<div class="hd-act">' +
      '<button class="icbtn" data-act="tab" data-id="cal">' + svg('cal', 20) + '</button>' +
      '<button class="icbtn accent" data-act="add">' + svg('plus', 20) + '</button>' +
      '</div></div>';
  }

  function heroHtml(r) {
    var k = C.KINDS[r.it.kind];
    var d = C.diffText(r.i.diff);
    var num = d.txt
      ? '<div class="hero-num txt">' + d.main + '</div>'
      : '<div class="hero-num">' + d.main + '</div><div class="hero-unit">天后</div>';

    var chips = [];
    chips.push(k.label);
    if (r.it.relation) chips.push(r.it.relation);
    if (r.i.age != null && r.i.age > 0) chips.push('满 ' + r.i.age + ' 岁');
    if (r.i.term) chips.push(r.i.term);
    chips.push(r.it.repeat === 'once' ? '只此一次' : '每年');

    var meta = r.i.y + '年' + r.i.m + '月' + r.i.d + '日 周' + L.WEEK_CN[r.i.wd];
    if (r.i.lun) meta += ' · 农历' + L.lunarText(r.i.lun.month, r.i.lun.day, r.i.lun.isLeap);

    return '<div class="hero" style="--c1:' + k.c1 + ';--c2:' + k.c2 + '" data-act="open" data-id="' + r.it.id + '">' +
      '<div class="hero-top"><span class="hero-tag">最近</span></div>' +
      '<div class="hero-main">' + num + '</div>' +
      '<div class="hero-name">' + esc(r.it.name) +
      (r.it.pinned ? '<span class="pin" style="margin-left:6px">★</span>' : '') + '</div>' +
      '<div class="hero-meta">' + esc(meta) + '</div>' +
      '<div class="hero-chips">' + chips.map(function (c) { return '<span>' + esc(c) + '</span>'; }).join('') + '</div>' +
      '</div>';
  }

  function itemHtml(r) {
    var k = C.KINDS[r.it.kind];
    var d = C.diffText(r.i.diff);
    var cls = 'item-d' + (d.txt ? ' txt' : '') + (d.hot ? ' hot' : '');
    var style = r.i.passed ? ' style="color:var(--text-3)"' : '';
    return '<div class="item" data-act="open" data-id="' + r.it.id + '">' +
      '<span class="badge" style="--c1:' + k.c1 + ';--c2:' + k.c2 + '">' + k.ch + '</span>' +
      '<div class="item-b">' +
      '<div class="item-n">' + esc(r.it.name) +
      (r.it.pinned ? '<span class="pin">★</span>' : '') +
      (r.it.repeat === 'once' ? '<span class="tag">一次性</span>' : '') +
      '</div>' +
      '<div class="item-s">' + esc(C.subText(r.it, r.i)) + '</div>' +
      '</div>' +
      '<div class="item-r"><div class="' + cls + '"' + style + '>' + d.main + '</div>' +
      (d.sub ? '<div class="item-l">' + d.sub + '</div>' : '') + '</div>' +
      '</div>';
  }

  function emptyHtml() {
    return '<div class="empty">' +
      '<div class="art">' +
      '<svg width="104" height="104" viewBox="0 0 104 104" fill="none">' +
      '<rect x="14" y="24" width="76" height="66" rx="16" fill="var(--accent-soft)"/>' +
      '<rect x="14" y="24" width="76" height="22" rx="16" fill="var(--accent)" opacity=".85"/>' +
      '<rect x="32" y="16" width="9" height="18" rx="4.5" fill="var(--accent)"/>' +
      '<rect x="63" y="16" width="9" height="18" rx="4.5" fill="var(--accent)"/>' +
      '<path d="M52 82l-1.6-1.5C42 72.6 38 68.9 38 64.3c0-3.7 2.9-6.6 6.6-6.6 2.1 0 4.1 1 5.4 2.5 1.3-1.5 3.3-2.5 5.4-2.5 3.7 0 6.6 2.9 6.6 6.6 0 4.6-4 8.3-12.4 16.2L52 82z" fill="var(--accent)"/>' +
      '</svg></div>' +
      '<h3>先把重要的日子记下来</h3>' +
      '<p>家人的生日、结婚纪念日、还有那些一年只提醒一次的事 —— 记一次，往后每年它自己会来找你。</p>' +
      '<button class="btn primary" data-act="add">添加第一个日子</button>' +
      '<div style="height:10px"></div>' +
      '<button class="btn ghost" data-act="presets">一键添加常用节日</button>' +
      '<div style="height:10px"></div>' +
      '<button class="btn ghost" data-act="fpresets">先看看示例数据</button>' +
      '</div>';
  }

  // ---------------------------------------------------------------- 全部

  function filteredRows() {
    var ti = todayInfo();
    var rows = C.sortByDate(C.decorate(S.items, ti.t));
    if (query) {
      var q = query.toLowerCase();
      rows = rows.filter(function (r) {
        return (r.it.name + ' ' + r.it.note + ' ' + r.it.relation).toLowerCase().indexOf(q) >= 0;
      });
    }
    if (filterKind !== 'all') {
      rows = rows.filter(function (r) { return r.it.kind === filterKind; });
    }
    if (sortMode === 'name') rows = C.sortByName(rows);
    else if (sortMode === 'created') rows = C.sortByCreated(rows);
    return rows;
  }

  function allListHtml(rows) {
    if (!rows.length) {
      return '<div class="empty"><h3>没有匹配的日子</h3><p>换个关键词，或者点右上角加一个。</p></div>';
    }
    return '<div class="grp-h">显示 ' + rows.length + ' 条</div>' +
      '<div class="list">' + rows.map(itemHtml).join('') + '</div>';
  }

  V.all = function () {
    var rows = filteredRows();

    var kinds = [{ k: 'all', n: '全部' }].concat(C.KIND_ORDER.map(function (k) {
      return { k: k, n: C.KINDS[k].label };
    }));

    var out = '<div class="hd"><div><div class="hd-day">全部日子</div>' +
      '<div class="hd-sub">一共记了 ' + S.items.length + ' 条</div></div>' +
      '<div class="hd-act"><button class="icbtn accent" data-act="add">' + svg('plus', 20) + '</button></div></div>';

    out += '<div class="search">' + svg('search', 18) +
      '<input id="q" type="search" placeholder="搜名字、备注、关系" value="' + esc(query) + '"></div>';

    out += '<div class="chips">' + kinds.map(function (k) {
      return '<button class="chip' + (filterKind === k.k ? ' on' : '') + '" data-act="filter" data-id="' + k.k + '">' + k.n + '</button>';
    }).join('') + '</div>';

    out += '<div class="chips">' + [['date', '按临近'], ['name', '按名字'], ['created', '按添加']].map(function (s) {
      return '<button class="chip' + (sortMode === s[0] ? ' on' : '') + '" data-act="sort" data-id="' + s[0] + '">' + s[1] + '</button>';
    }).join('') + '</div>';

    out += '<div id="allList">' + allListHtml(rows) + '</div>';
    return out;
  };

  // ---------------------------------------------------------------- 日历

  V.cal = function () {
    var ti = todayInfo();
    if (!calY) { calY = ti.y.y; calM = ti.y.m; }
    if (calSel == null) calSel = ti.t;

    var first = L.ymdToNum(calY, calM, 1);
    var firstWd = L.weekdayOfNum(first);
    var start = first - firstWd;

    // 把「这 42 格里每一天有哪些日子」算出来。
    // 不能只看今天往后的下一次发生 —— 那样翻到过去的月份生日就不显示圆点了。
    var gridEnd = start + 41;
    var dayItems = {};
    function markOn(n, it) {
      if (n < start || n > gridEnd) return;
      if (!dayItems[n]) dayItems[n] = [];
      if (dayItems[n].length < 4) dayItems[n].push(it);
    }
    S.items.forEach(function (it) {
      if (it.repeat === 'once') {
        var n0 = (it.dateType === 'lunar')
          ? L.numOfLunar(it.year, it.month, it.day, it.isLeap)
          : L.ymdToNum(it.year, it.month, it.day);
        if (n0 != null) markOn(n0, it);
        return;
      }
      var cur = start;
      for (var guard = 0; guard < 10; guard++) {
        var n = L.nextOccurrence(it, cur, 'year', true);
        if (n == null || n > gridEnd) break;
        markOn(n, it);
        cur = n + 1;
      }
    });

    function idSet(list) {
      var s = {};
      (list || []).forEach(function (it) { s[it.id] = 1; });
      return s;
    }

    var cells = '';
    for (var i = 0; i < 42; i++) {
      var n = start + i;
      var ymd = L.numToYmd(n);
      var outMonth = ymd.m !== calM;
      var lun = L.solarToLunar(ymd.y, ymd.m, ymd.d);
      var term = L.solarTerm(ymd.y, ymd.m, ymd.d);
      var lab = term ? term : (lun ? L.lunarShort(lun) : '');
      var cls = 'cal-d';
      if (outMonth) cls += ' out';
      if (n === ti.t) cls += ' today';
      if (n === calSel) cls += ' sel';
      if (L.weekdayOfNum(n) === 0 || L.weekdayOfNum(n) === 6) cls += ' wend';
      var marks = dayItems[n] || [];
      var dots = marks.length ? '<span class="dots">' + marks.slice(0, 3).map(function (x) {
        return '<i style="background:' + C.KINDS[x.kind].c1 + '"></i>';
      }).join('') + '</span>' : '';
      cells += '<div class="' + cls + '" data-act="calsel" data-id="' + n + '">' +
        '<span class="g">' + ymd.d + '</span>' +
        '<span class="l' + (term ? ' term' : '') + '">' + esc(lab) + '</span>' + dots + '</div>';
    }

    var selYmd = L.numToYmd(calSel);
    var selLun = L.solarToLunar(selYmd.y, selYmd.m, selYmd.d);
    // 行内容仍按「今天」为基准渲染 —— 全 App 的行都表示「下一次是什么时候」，
    // 这样翻到过去某天也不会出现「今天」这种自相矛盾的角标。
    var selSet = idSet(dayItems[calSel]);
    var selRows = C.decorate(S.items, ti.t).filter(function (r) { return selSet[r.it.id]; });

    var selTitle = selYmd.m + '月' + selYmd.d + '日 周' + L.WEEK_CN[L.weekdayOfNum(calSel)];
    var selSub = (selLun ? '农历' + L.lunarText(selLun.month, selLun.day, selLun.isLeap) : '');
    var selTerm = L.solarTerm(selYmd.y, selYmd.m, selYmd.d);
    if (selTerm) selSub += (selSub ? ' · ' : '') + selTerm;

    return '<div class="hd"><div><div class="hd-day">日历</div>' +
      '<div class="hd-sub">有安排的日子会亮起小圆点</div></div>' +
      '<div class="hd-act"><button class="icbtn accent" data-act="add">' + svg('plus', 20) + '</button></div></div>' +

      '<div class="calbar">' +
      '<button class="navbtn" data-act="calprev">' + svg('left', 18) + '</button>' +
      '<b>' + calY + '年' + calM + '月' + (calM === ti.y.m && calY === ti.y.y ? '<small>本月</small>' : '') + '</b>' +
      '<div style="display:flex;gap:8px">' +
      '<button class="navbtn" data-act="caltoday">今</button>' +
      '<button class="navbtn" data-act="calnext">' + svg('right', 18) + '</button>' +
      '</div></div>' +

      '<div class="cal">' +
      L.WEEK_CN.map(function (w) { return '<div class="cal-w">' + w + '</div>'; }).join('') +
      cells + '</div>' +

      '<div class="sec"><h2>' + selTitle + '</h2><span>' + esc(selSub) + '</span></div>' +
      (selRows.length
        ? '<div class="list">' + selRows.map(itemHtml).join('') + '</div>'
        : '<div class="grp-h" style="margin:2px 4px 0">这天没有安排</div>' +
        '<div style="margin-top:14px"><button class="btn ghost" data-act="addon" data-id="' + calSel + '">把日子加在这一天</button></div>') +

      '<div class="sec-t">本月节气</div>' +
      '<div class="card">' + L.termsOfMonth(calY, calM).map(function (x) {
        return '<div class="row"><div class="row-m"><div class="row-t">' + x.name + '</div></div>' +
          '<div class="row-v">' + calM + '月' + x.day + '日</div></div>';
      }).join('') + '</div>';
  };

  // ---------------------------------------------------------------- 设置

  V.me = function () {
    var info = nInfo() || {};
    var granted = info.notifyGranted !== false;
    var exact = info.exactAlarm !== false;
    var themeName = { system: '跟随系统', light: '浅色', dark: '深色' }[S.settings.theme];

    var out = '<div class="hd"><div><div class="hd-day">设置</div>' +
      '<div class="hd-sub">提醒、外观与数据</div></div></div>';

    out += '<div class="sec-t">外观</div><div class="card">' +
      '<div class="row"><div class="row-m"><div class="row-t">主题</div></div>' +
      '<div class="seg" style="width:200px">' +
      [['system', '自动'], ['light', '浅色'], ['dark', '深色']].map(function (x) {
        return '<button class="' + (S.settings.theme === x[0] ? 'on' : '') + '" data-act="theme" data-id="' + x[0] + '">' + x[1] + '</button>';
      }).join('') + '</div></div>' +
      '<div class="row"><div class="row-m"><div class="row-t">当前</div></div>' +
      '<div class="row-v">' + themeName + '</div></div></div>';

    out += '<div class="sec-t">提醒</div><div class="card">' +
      '<div class="row"><div class="row-m"><div class="row-t">开启提醒</div>' +
      '<div class="row-s">关掉后不会推送任何通知，App 内倒计时照常</div></div>' +
      toggle(S.settings.notifyOn, 'notifyOn') + '</div>' +

      '<button class="row" data-act="notifyReq"><div class="row-m"><div class="row-t">通知权限</div>' +
      '<div class="row-s">' + (granted ? '已允许，可以正常推送' : '未允许，点这里去申请') + '</div></div>' +
      '<div class="row-v ' + (granted ? 'ok' : 'no') + '">' + (granted ? '已开启' : '去开启') + '</div></button>' +

      '<button class="row" data-act="exactAlarm"><div class="row-m"><div class="row-t">准点提醒</div>' +
      '<div class="row-s">部分系统默认只允许"大致准时"，开启后可精确到分钟</div></div>' +
      '<div class="row-v ' + (exact ? 'ok' : 'no') + '">' + (exact ? '精确' : '待优化') + '</div></button>' +

      '<div class="row"><div class="row-m"><div class="row-t">提醒时间</div></div>' +
      '<input class="inp" type="time" id="rtime" value="' + esc(S.settings.remindTime) + '" style="width:130px;padding:9px 12px"></div>' +

      '<div class="row" style="flex-wrap:wrap"><div class="row-m"><div class="row-t">默认提前</div>' +
      '<div class="row-s">新建日子时默认勾选的提前天数</div></div>' +
      '<div class="picks" style="width:100%;margin-top:10px">' + C.REMIND_OPTS.map(function (d) {
        var on = S.settings.defaultRemind.indexOf(d) >= 0;
        return '<button class="pick' + (on ? ' on' : '') + '" data-act="dremind" data-id="' + d + '">' +
          (d === 0 ? '当天' : d + ' 天前') + '</button>';
      }).join('') + '</div></div>' +

      '<button class="row" data-act="notifyTest"><div class="row-m"><div class="row-t">发一条测试通知</div>' +
      '<div class="row-s">验证提醒链路是否真的通</div></div><div class="row-v">试试</div></button>' +
      '</div>';

    out += '<div class="sec-t">数据</div><div class="card">' +
      '<button class="row" data-act="fexport"><div class="row-m"><div class="row-t">导出备份</div>' +
      '<div class="row-s">存成 JSON 文件到「下载」目录</div></div><div class="row-v">导出</div></button>' +
      '<button class="row" data-act="fimport"><div class="row-m"><div class="row-t">导入备份</div>' +
      '<div class="row-s">从备份文件恢复，可选择覆盖或合并</div></div><div class="row-v">导入</div></button>' +
      '<button class="row" data-act="fshare"><div class="row-m"><div class="row-t">分享日子清单</div>' +
      '<div class="row-s">生成一份文字清单，发给家人或存进备忘</div></div><div class="row-v">分享</div></button>' +
      '<button class="row" data-act="presets"><div class="row-m"><div class="row-t">添加常用节日</div>' +
      '<div class="row-s">春节、中秋、除夕等，一次加齐</div></div><div class="row-v">添加</div></button>' +
      '<button class="row" data-act="fsample"><div class="row-m"><div class="row-t">载入示例数据</div>' +
      '<div class="row-s">看看这个 App 大概是什么样子</div></div><div class="row-v">载入</div></button>' +
      '<button class="row" data-act="fclear"><div class="row-m"><div class="row-t" style="color:var(--accent)">清空所有数据</div>' +
      '<div class="row-s">不可恢复，建议先导出备份</div></div><div class="row-v no">清空</div></button>' +
      '</div>';

    out += '<div class="sec-t">关于</div>' +
      '<div class="about">' +
      '<div class="logo">' + svg('cal', 30) + '</div>' +
      '<div class="nm">好日子</div>' +
      '<div class="vs">版本 ' + esc(info.version || '1.0') + ' · 农历算法 1900–2100</div>' +
      (info.safeTop === undefined ? '' :
        '<div class="vs">安全区 上 ' + info.safeTop + ' / 下 ' + info.safeBottom + ' px</div>') +
      '<div class="ds">把重要的日子记一次，往后每年它自己会来找你。<br>' +
      '所有数据只存在这台手机上，不联网、不上传。<br>' +
      '农历三十遇到小月时，按「廿九当三十过」处理。</div>' +
      '</div>' +
      '<div style="height:20px"></div>';

    return out;
  };

  function toggle(on, act) {
    return '<button class="pick' + (on ? ' on' : '') + '" data-act="' + act + '" data-id="' + (on ? '0' : '1') + '" ' +
      'style="min-width:64px;text-align:center">' + (on ? '已开启' : '已关闭') + '</button>';
  }

  // ---------------------------------------------------------------- 弹层

  function openSheet(html) {
    $('sheet').innerHTML = html;
    $('sheetWrap').classList.add('on');
    sheetOpen = true;
    nBack(true);
  }

  function closeSheet() {
    $('sheetWrap').classList.remove('on');
    sheetOpen = false;
    draft = null;
    nBack(view !== 'home');
  }

  // ---- 详情
  function openDetail(id) {
    var it = findItem(id);
    if (!it) return;
    var ti = todayInfo();
    var i = C.info(it, ti.t);
    var k = C.KINDS[it.kind];

    var body = '<div class="dhero" style="--c1:' + k.c1 + ';--c2:' + k.c2 + '">' +
      '<div class="t">' + esc(it.name) + '</div>' +
      '<div class="d">' + esc(C.ruleText(it)) + k.label + '</div>';

    if (i) {
      var d = C.diffText(i.diff);
      body += '<div class="n">' + (d.txt ? d.main : d.main + ' <small>' + (i.diff < 0 ? '天前' : '天后') + '</small>') + '</div>';
    }
    body += '</div>';

    var kvs = [];
    if (i) {
      kvs.push(['下次', i.y + '年' + i.m + '月' + i.d + '日 周' + L.WEEK_CN[i.wd]]);
      if (i.lun) kvs.push(['对应农历', L.lunarText(i.lun.month, i.lun.day, i.lun.isLeap)]);
      if (i.term) kvs.push(['节气', i.term]);
      if (i.age != null && i.age > 0) kvs.push(['届时年龄', i.age + ' 周岁']);
      if (i.prevNum != null && i.diff >= 0) {
        var py = L.numToYmd(i.prevNum);
        kvs.push(['上次', py.y + '年' + py.m + '月' + py.d + '日（' + C.sinceText(i.sincePrev) + '）']);
      }
      if (i.passed) kvs.push(['状态', '已经过去 ' + Math.abs(i.diff) + ' 天']);
    }
    kvs.push(['重复', it.repeat === 'once' ? '只此一次' : '每年一次']);
    if (it.relation) kvs.push(['关系', it.relation]);
    kvs.push(['提前提醒', (it.remind && it.remind.length ? it.remind : S.settings.defaultRemind)
      .map(function (d) { return d === 0 ? '当天' : d + ' 天前'; }).join('、') || '不提醒']);
    kvs.push(['提醒时间', S.settings.remindTime]);
    if (it.note) kvs.push(['备注', it.note]);
    kvs.push(['添加于', new Date(it.createdAt).toLocaleDateString('zh-CN')]);

    body += '<div class="card">' + kvs.map(function (kv) {
      return '<div class="kv"><span>' + esc(kv[0]) + '</span><b>' + esc(kv[1]) + '</b></div>';
    }).join('') + '</div>';

    body += '<div class="btnrow"><button class="btn ghost" data-act="pin" data-id="' + it.id + '">' +
      (it.pinned ? '取消置顶' : '置顶') + '</button>' +
      '<button class="btn ghost" data-act="shareOne" data-id="' + it.id + '">分享</button></div>';
    body += '<div class="btnrow"><button class="btn primary" data-act="edit" data-id="' + it.id + '">编辑</button></div>';
    body += '<div class="btnrow"><button class="btn danger" data-act="del" data-id="' + it.id + '">删除这条</button></div>';
    body += '<div style="height:20px"></div>';

    openSheet('<div class="sheet-hd"><b>日子详情</b><button data-act="close">关闭</button></div>' +
      '<div class="detail">' + body + '</div>');
  }

  // ---- 编辑
  function openEditor(id, presetDateNum) {
    var it = id ? JSON.parse(JSON.stringify(findItem(id))) : null;
    var ti = todayInfo();

    if (!it) {
      it = C.fixItem({
        kind: 'birthday', name: '', dateType: 'solar',
        month: ti.y.m, day: ti.y.d, repeat: 'year', remind: S.settings.defaultRemind.slice()
      });
      if (presetDateNum != null) {
        var pd = L.numToYmd(presetDateNum);
        it.month = pd.m; it.day = pd.d;
      }
    }
    draft = it;
    renderEditor(id ? '编辑' : '新建日子');
  }

  function renderEditor(title) {
    var it = draft;
    var isLunar = it.dateType === 'lunar';
    var isOnce = it.repeat === 'once';
    var maxDay = isLunar ? 30 : C.solarMonthDays(it.month);

    var monthOpts = arr(1, 12).map(function (m) {
      return { v: m, t: isLunar ? (L.lunarMonthName(m) + '月') : (m + ' 月') };
    });

    var dayOpts = isLunar
      ? arr(1, maxDay).map(function (d) { return { v: d, t: L.lunarDayName(d) }; })
      : arr(1, maxDay).map(function (d) { return { v: d, t: d + ' 日' }; });

    var html = '<div class="sheet-hd">' +
      '<button data-act="close">取消</button><b>' + title + '</b>' +
      '<button class="save" data-act="save">保存</button></div>' +
      '<div class="form">';

    // 类型
    html += '<div class="fld"><label>这是什么日子</label><div class="seg">' +
      C.KIND_ORDER.map(function (k) {
        return '<button class="' + (it.kind === k ? ' on' : '') + '" data-act="fkind" data-id="' + k + '">' +
          C.KINDS[k].label + '</button>';
      }).join('') + '</div></div>';

    // 名字
    html += '<div class="fld"><label>' + (it.kind === 'birthday' ? '是谁' : '叫什么') + '</label>' +
      '<input class="inp" id="f-name" maxlength="40" placeholder="' +
      (it.kind === 'birthday' ? '比如：妈妈' : '比如：结婚纪念日') + '" value="' + esc(it.name) + '"></div>';

    // 关系
    if (it.kind === 'birthday' || it.kind === 'anniversary') {
      html += '<div class="fld"><label>关系（可不填）</label><div class="picks">' +
        C.RELATIONS.map(function (r) {
          return '<button class="pick' + (it.relation === r ? ' on' : '') + '" data-act="frel" data-id="' + r + '">' + r + '</button>';
        }).join('') + '</div></div>';
    }

    // 历法
    html += '<div class="fld"><label>用哪种历法</label><div class="seg">' +
      '<button class="' + (!isLunar ? ' on' : '') + '" data-act="fdt" data-id="solar">公历</button>' +
      '<button class="' + (isLunar ? ' on' : '') + '" data-act="fdt" data-id="lunar">农历</button>' +
      '</div>' +
      '<div class="hint">' + (isLunar
        ? '家里的长辈多半记的是农历生日，选这个就对了。'
        : '身份证、结婚证上的日期一般按公历。') + '</div></div>';

    // 日期
    html += '<div class="fld"><label>' + (isLunar ? '农历日期' : '公历日期') + '</label>' +
      '<div class="two">' +
      '<select class="inp" id="f-month">' + monthOpts.map(function (o) {
        return '<option value="' + o.v + '"' + (o.v === it.month ? ' selected' : '') + '>' + o.t + '</option>';
      }).join('') + '</select>' +
      '<select class="inp" id="f-day">' + dayOpts.map(function (o) {
        return '<option value="' + o.v + '"' + (o.v === it.day ? ' selected' : '') + '>' + o.t + '</option>';
      }).join('') + '</select>' +
      '</div>';

    if (isLunar && C.hasLeapNear(it.month, todayInfo().y.y, 12)) {
      html += '<div style="margin-top:8px"><button class="pick' + (it.isLeap ? ' on' : '') +
        '" data-act="fleap" data-id="' + (it.isLeap ? '0' : '1') + '">这是闰' + L.lunarMonthName(it.month) + '月</button></div>';
    }
    if (isLunar && C.lunarDayMayShift(it.month, it.day)) {
      html += '<div class="hint">遇上只有廿九的小月时，会按「廿九当三十过」顺延到当月最后一天。</div>';
    }
    html += '</div>';

    // 年份 + 重复
    html += '<div class="fld"><label>' + (it.kind === 'birthday' ? '出生年份（可留空）' : '年份（可留空）') + '</label>' +
      '<input class="inp" id="f-year" type="number" inputmode="numeric" placeholder="留空 = 不显示年龄、每年重复" value="' +
      (it.year == null ? '' : it.year) + '">' +
      '<div class="hint">' + (it.kind === 'birthday'
        ? '填了才能算出「今年多大」。'
        : '填了就能算出「第几年」。') + '</div></div>';

    html += '<div class="fld"><label>重复</label><div class="seg">' +
      '<button class="' + (!isOnce ? ' on' : '') + '" data-act="frp" data-id="year">每年都过</button>' +
      '<button class="' + (isOnce ? ' on' : '') + '" data-act="frp" data-id="once">只过一次</button>' +
      '</div></div>';

    // 提醒
    html += '<div class="fld"><label>提前提醒</label><div class="picks">' +
      C.REMIND_OPTS.map(function (d) {
        var on = (it.remind || []).indexOf(d) >= 0;
        return '<button class="pick' + (on ? ' on' : '') + '" data-act="fremind" data-id="' + d + '">' +
          (d === 0 ? '当天' : d + ' 天前') + '</button>';
      }).join('') + '</div>' +
      '<div class="hint">提醒会以系统通知的形式推送，时间在「设置」里统一调整。</div></div>';

    // 备注
    html += '<div class="fld"><label>备注（可不填）</label>' +
      '<textarea class="inp" id="f-note" rows="3" maxlength="200" placeholder="比如：喜欢吃什么蛋糕、礼物清单…">' +
      esc(it.note) + '</textarea></div>';

    html += '<div style="height:10px"></div></div>';

    openSheet(html);
    bindEditor();
  }

  function arr(a, b) { var o = []; for (var i = a; i <= b; i++) o.push(i); return o; }

  function bindEditor() {
    var nameEl = $('f-name'), noteEl = $('f-note'), yearEl = $('f-year');
    if (nameEl) nameEl.addEventListener('input', function () { draft.name = this.value; });
    if (noteEl) noteEl.addEventListener('input', function () { draft.note = this.value; });
    if (yearEl) yearEl.addEventListener('input', function () {
      var v = parseInt(this.value, 10);
      draft.year = isNaN(v) ? null : v;
    });
    var mEl = $('f-month'), dEl = $('f-day');
    if (mEl) mEl.addEventListener('change', function () {
      draft.month = parseInt(this.value, 10);
      var max = draft.dateType === 'lunar' ? 30 : C.solarMonthDays(draft.month);
      if (draft.day > max) draft.day = max;
      renderEditor($('sheet').querySelector('b').textContent);
    });
    if (dEl) dEl.addEventListener('change', function () {
      draft.day = parseInt(this.value, 10);
    });
  }

  // ---- 通用确认框
  var confirmResolve = null;

  function ask(opts) {
    return new Promise(function (resolve) {
      confirmResolve = resolve;
      openSheet('<div class="sheet-hd"><b>' + esc(opts.title) + '</b><button data-act="askno">关闭</button></div>' +
        '<div class="detail"><div style="padding:14px 2px 6px;font-size:14px;line-height:1.7;color:var(--text-2)">' +
        opts.body + '</div>' +
        '<div class="btnrow">' + (opts.buttons || []).map(function (b, k) {
          return '<button class="btn ' + (b.style || 'ghost') + '" data-act="askyes" data-id="' + k + '">' + esc(b.label) + '</button>';
        }).join('') + '</div><div style="height:20px"></div></div>');
    });
  }

  // ---- 节日预设
  var presetPick = null;

  function openPresets() {
    presetPick = {};
    C.PRESET_LUNAR.forEach(function (p) { presetPick['l' + p.m + '_' + p.d] = true; });
    renderPresets();
  }

  function renderPresets() {
    var groups = [
      { t: '农历节日', list: C.PRESET_LUNAR, pre: 'l', sub: function (p) { return '农历' + L.lunarMonthName(p.m) + '月' + L.lunarDayName(p.d); } },
      { t: '公历节日', list: C.PRESET_SOLAR, pre: 's', sub: function (p) { return p.m + '月' + p.d + '日'; } }
    ];
    var html = '<div class="sheet-hd"><button data-act="close">取消</button><b>常用节日</b>' +
      '<button class="save" data-act="presetadd">添加</button></div><div class="form">';

    groups.forEach(function (g) {
      html += '<div class="sec-t" style="margin-top:16px">' + g.t + '</div><div class="picks" style="margin-top:10px">';
      g.list.forEach(function (p) {
        var key = g.pre + p.m + '_' + p.d;
        var on = !!presetPick[key];
        html += '<button class="pick' + (on ? ' on' : '') + '" data-act="ptoggle" data-id="' + key + '" ' +
          'style="min-width:86px;text-align:center">' + p.name + '<br><span style="font-size:10.5px;opacity:.7">' +
          g.sub(p) + '</span></button>';
      });
      html += '</div>';
    });

    html += '<div class="btnrow"><button class="btn ghost" data-act="pall">全选</button>' +
      '<button class="btn ghost" data-act="pnone">全不选</button></div>';
    html += '<div class="hint" style="margin-top:14px">已经存在的同名节日不会重复添加。</div>';
    html += '<div style="height:10px"></div></div>';
    openSheet(html);
  }

  // ---------------------------------------------------------------- 动作

  var ACT = {
    tab: function (v) { view = v; render(); window.scrollTo(0, 0); },
    add: function () { openEditor(null); },
    addon: function (id) { openEditor(null, parseInt(id, 10)); },
    open: function (id) { openDetail(id); },
    close: function () { closeSheet(); },
    askno: function () { if (confirmResolve) confirmResolve(-1); confirmResolve = null; closeSheet(); },
    askyes: function (k) { if (confirmResolve) confirmResolve(parseInt(k, 10)); confirmResolve = null; closeSheet(); },

    filter: function (k) { filterKind = k; render(); },
    sort: function (k) { sortMode = k; render(); },

    calprev: function () { calM--; if (calM < 1) { calM = 12; calY--; } if (calY < 1901) { calY = 1901; calM = 1; } render(); },
    calnext: function () { calM++; if (calM > 12) { calM = 1; calY++; } if (calY > 2099) { calY = 2099; calM = 12; } render(); },
    caltoday: function () { var ti = todayInfo(); calY = ti.y.y; calM = ti.y.m; calSel = ti.t; render(); },
    calsel: function (id) { calSel = parseInt(id, 10); var y = L.numToYmd(calSel); if (y.m !== calM) { calM = y.m; calY = y.y; } render(); },

    theme: function (v) {
      S.settings.theme = v;
      persist(); applyTheme(); render();
    },
    notifyOn: function (v) {
      S.settings.notifyOn = v === '1';
      persist(); render();
      toast(S.settings.notifyOn ? '提醒已开启' : '提醒已关闭');
    },
    dremind: function (d) {
      d = parseInt(d, 10);
      var i = S.settings.defaultRemind.indexOf(d);
      if (i >= 0) S.settings.defaultRemind.splice(i, 1);
      else { S.settings.defaultRemind.push(d); S.settings.defaultRemind.sort(function (a, b) { return b - a; }); }
      persist(); render();
    },
    notifyReq: function () { try { native && native.requestNotify(); } catch (e) { } nVib(); },
    exactAlarm: function () { try { native && native.openExactAlarmSettings(); } catch (e) { } },
    notifyTest: function () { try { native && native.testNotification(); } catch (e) { toast('当前环境不支持'); } },

    // ---- 编辑器
    fkind: function (k) { draft.kind = k; renderEditor(editorTitle()); },
    frel: function (r) { draft.relation = draft.relation === r ? '' : r; renderEditor(editorTitle()); },
    fdt: function (v) {
      draft.dateType = v;
      if (v === 'lunar') {
        // 公历->农历，保留同一个"日子"更直观：这里只做区间夹取
        if (draft.day > 30) draft.day = 30;
        draft.isLeap = false;
      } else if (draft.day > 31) draft.day = 31;
      renderEditor(editorTitle());
    },
    fleap: function (v) { draft.isLeap = v === '1'; renderEditor(editorTitle()); },
    frp: function (v) {
      draft.repeat = v;
      if (v === 'once' && draft.year == null) draft.year = todayInfo().y.y;
      renderEditor(editorTitle());
    },
    fremind: function (d) {
      d = parseInt(d, 10);
      if (!Array.isArray(draft.remind)) draft.remind = [];
      var i = draft.remind.indexOf(d);
      if (i >= 0) draft.remind.splice(i, 1);
      else { draft.remind.push(d); draft.remind.sort(function (a, b) { return b - a; }); }
      renderEditor(editorTitle());
    },
    save: function () {
      // 表单里可能还有未失焦的输入，统一收一遍
      var n = $('f-name'), nt = $('f-note'), y = $('f-year');
      if (n) draft.name = n.value;
      if (nt) draft.note = nt.value;
      if (y) {
        var v = parseInt(y.value, 10);
        draft.year = isNaN(v) ? null : v;
      }
      if (!String(draft.name).trim()) { toast('给它起个名字吧'); if (n) n.focus(); return; }

      var item = C.fixItem(draft);
      var idx = -1;
      for (var k = 0; k < S.items.length; k++) if (S.items[k].id === item.id) { idx = k; break; }
      if (idx >= 0) S.items[idx] = item; else S.items.push(item);

      persist();
      closeSheet();
      render();
      nVib();
      toast(idx >= 0 ? '已保存' : '已添加「' + item.name + '」');
    },

    // ---- 详情 / 列表
    pin: function (id) {
      var it = findItem(id);
      if (!it) return;
      it.pinned = !it.pinned;
      persist(); render(); openDetail(id);
      toast(it.pinned ? '已置顶' : '已取消置顶');
    },
    shareOne: function (id) {
      var it = findItem(id);
      if (!it) return;
      var i = C.info(it, todayInfo().t);
      var d = i ? C.diffText(i.diff).txt ? C.diffText(i.diff).main : C.diffText(i.diff).main + ' 天后' : '';
      var txt = '【' + C.KINDS[it.kind].label + '】' + it.name + '\n' +
        C.ruleText(it) + '\n' +
        (i ? '下次：' + i.y + '年' + i.m + '月' + i.d + '日（' + d + '）' +
          (i.lun ? '，农历' + L.lunarText(i.lun.month, i.lun.day, i.lun.isLeap) : '') : '') +
        (it.note ? '\n备注：' + it.note : '') +
        '\n\n—— 来自「好日子」';
      try { native ? native.shareText(txt) : toast('当前环境不支持分享'); } catch (e) { }
    },
    del: function (id) {
      var it = findItem(id);
      if (!it) return;
      ask({
        title: '删除「' + it.name + '」',
        body: '删掉之后就找不回来了。如果只是想暂时不看，可以取消置顶、不用管它。',
        buttons: [{ label: '再想想', style: 'ghost' }, { label: '确认删除', style: 'danger' }]
      }).then(function (k) {
        if (k !== 1) return;
        S.items = S.items.filter(function (x) { return x.id !== id; });
        persist(); render(); toast('已删除');
      });
    },

    // ---- 数据
    fexport: function () {
      var name = '好日子备份-' + stamp() + '.json';
      var txt = C.exportPayload(S);
      try {
        var r = native ? native.exportBackup(name, txt) : null;
        toast(r || '当前环境不支持导出');
      } catch (e) { toast('导出失败'); }
    },
    fimport: function () {
      closeSheet();
      try { native ? native.pickBackup() : toast('当前环境不支持导入'); } catch (e) { toast('导入失败'); }
    },
    fshare: function () {
      if (!S.items.length) { toast('还没有可分享的日子'); return; }
      try { native ? native.shareText(C.shareText(S.items)) : toast('当前环境不支持分享'); } catch (e) { }
    },
    fpresets: function () {
      var items = C.sampleItems();
      S.items = S.items.concat(items);
      persist(); closeSheet(); view = 'home'; render();
      toast('已载入 ' + items.length + ' 条示例数据，可在「全部」里删掉');
    },
    fsample: function () { ACT.fpresets(); },
    fclear: function () {
      ask({
        title: '清空所有数据',
        body: '会把 <b>' + S.items.length + '</b> 条日子全部删掉，包括你手动添加的。这个操作不可恢复——<br>建议先在「导出备份」里存一份。',
        buttons: [{ label: '取消', style: 'ghost' }, { label: '确认清空', style: 'danger' }]
      }).then(function (k) {
        if (k !== 1) return;
        S.items = [];
        persist(); view = 'home'; render(); toast('已清空');
      });
    },

    // ---- 节日预设
    presets: function () { openPresets(); },
    ptoggle: function (k) { presetPick[k] = !presetPick[k]; renderPresets(); },
    pall: function () {
      C.PRESET_LUNAR.forEach(function (p) { presetPick['l' + p.m + '_' + p.d] = true; });
      C.PRESET_SOLAR.forEach(function (p) { presetPick['s' + p.m + '_' + p.d] = true; });
      renderPresets();
    },
    pnone: function () { presetPick = {}; renderPresets(); },
    presetadd: function () {
      var add = [];
      function take(list, pre, ltype) {
        list.forEach(function (p) {
          if (!presetPick[pre + p.m + '_' + p.d]) return;
          var dup = S.items.some(function (x) {
            return x.name === p.name && x.dateType === ltype && x.month === p.m && x.day === p.d;
          });
          if (dup) return;
          add.push(C.fixItem({
            kind: 'festival', name: p.name, dateType: ltype,
            month: p.m, day: p.d, repeat: 'year',
            remind: p.name === '除夕' || p.name === '春节' ? [15, 7, 1, 0] : [3, 1, 0]
          }));
        });
      }
      take(C.PRESET_LUNAR, 'l', 'lunar');
      take(C.PRESET_SOLAR, 's', 'solar');
      S.items = S.items.concat(add);
      persist(); closeSheet(); render();
      toast(add.length ? '已添加 ' + add.length + ' 个节日' : '没有新增（可能都已存在）');
    }
  };

  function editorTitle() {
    return ($('sheet').querySelector('.sheet-hd b') || {}).textContent || '新建日子';
  }

  function stamp() {
    var d = new Date();
    return d.getFullYear() + L.pad2(d.getMonth() + 1) + L.pad2(d.getDate()) + '-' +
      L.pad2(d.getHours()) + L.pad2(d.getMinutes());
  }

  // ---------------------------------------------------------------- 事件

  document.addEventListener('click', function (e) {
    var t = e.target.closest ? e.target.closest('[data-act]') : null;
    if (!t) return;
    var act = t.getAttribute('data-act');
    if (act === 'tab' || act === 'filter' || act === 'sort') nVib();
    if (ACT[act]) {
      e.preventDefault();
      ACT[act](t.getAttribute('data-id'), t);
    }
  });

  // 搜索框（全部页）—— 只重绘列表，避免每敲一个字整页闪一下
  document.addEventListener('input', function (e) {
    if (e.target.id === 'q') {
      query = e.target.value;
      var box = $('allList');
      if (box) box.innerHTML = allListHtml(filteredRows());
      return;
    }
    if (e.target.id === 'rtime') {
      S.settings.remindTime = e.target.value || '09:00';
      persist();
      toast('提醒时间已改为 ' + S.settings.remindTime);
    }
  });

  // 点遮罩关闭
  $('sheetMask').addEventListener('click', function () { closeSheet(); });

  // 系统主题变化
  if (window.matchMedia) {
    var mq = window.matchMedia('(prefers-color-scheme: dark)');
    var onMq = function () { if (S.settings.theme === 'system') applyTheme(); };
    if (mq.addEventListener) mq.addEventListener('change', onMq);
    else if (mq.addListener) mq.addListener(onMq);
  }

  // 原生回调
  window.HR = {
    onBack: function () {
      if (sheetOpen) { closeSheet(); return true; }
      if (view !== 'home') { view = 'home'; render(); window.scrollTo(0, 0); return true; }
      return false;
    },
    onResume: function () { refresh(); },
    refresh: function () { refresh(); },
    onNativeChange: function () {
      if (view === 'me') render();
    },
    onBackupLoaded: function (text) {
      var r = C.parseBackup(text);
      if (!r.ok) { toast(r.msg); return; }
      ask({
        title: '导入备份',
        body: '备份里有 <b>' + r.count + '</b> 条日子，当前有 <b>' + S.items.length + '</b> 条。<br><br>' +
          '<b>覆盖</b>：用备份里的内容替换现在全部数据<br>' +
          '<b>合并</b>：两边都留着，同一条以备份里的为准',
        buttons: [
          { label: '取消', style: 'ghost' },
          { label: '覆盖', style: 'primary' },
          { label: '合并', style: 'ok' }
        ]
      }).then(function (k) {
        if (k === 1) {
          S.items = r.store.items;
          S.settings = r.store.settings;
          persist(); applyTheme(); view = 'home'; render();
          toast('已覆盖导入 ' + r.count + ' 条');
        } else if (k === 2) {
          var before = S.items.length;
          S.items = C.mergeItems(S.items, r.store.items);
          persist(); render();
          toast('已合并，新增 ' + (S.items.length - before) + ' 条');
        }
      });
    }
  };

  function refresh() {
    if (sheetOpen) return;
    render();
  }

  // ---------------------------------------------------------------- 启动

  applyTheme();
  view = 'home';
  render();
  syncReminders();
})();
