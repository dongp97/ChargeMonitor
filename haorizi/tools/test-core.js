const L = require('../app/src/main/assets/lunar.js');
const C = require('../app/src/main/assets/core.js');

let pass = 0, fail = 0;
function eq(label, got, want) {
  const ok = typeof got === 'object' ? JSON.stringify(got) === JSON.stringify(want) : got === want;
  if (ok) pass++; else { fail++; console.log(`  ✗ ${label}: got ${JSON.stringify(got)}  want ${JSON.stringify(want)}`); }
}
function ok(label, cond, extra) {
  if (cond) pass++; else { fail++; console.log(`  ✗ ${label}${extra ? ' — ' + extra : ''}`); }
}

// 固定"今天"= 2026-09-10，让测试可复现
const T = L.ymdToNum(2026, 9, 10);
const NOW = new Date(2026, 8, 10, 8, 0, 0, 0).getTime(); // 当天早上 8 点

console.log('=== 1. fixItem 容错 ===');
eq('非法 kind 回退', C.fixItem({ name: 'x', kind: 'zzz' }).kind, 'countdown');
eq('空名字回退', C.fixItem({}).name, '未命名');
eq('2月31日被夹到29', C.fixItem({ name: 'x', dateType: 'solar', month: 2, day: 31 }).day, 29);
eq('月份越界夹回', C.fixItem({ name: 'x', month: 99, day: 1 }).month, 12);
eq('年份越界清空', C.fixItem({ name: 'x', year: 1800 }).year, null);
eq('repeat 非法回退 yearly', C.fixItem({ name: 'x', repeat: 'weekly' }).repeat, 'year');
eq('remind 排序去重（999 与负数被剔除）', C.fixItem({ name: 'x', remind: [1, 7, 1, 999, -2] }).remind, [7, 1]);
eq('一次性且无年份时补年份', typeof C.fixItem({ name: 'x', repeat: 'once' }).year, 'number');
eq('关系白名单过滤', C.fixItem({ name: 'x', relation: '路人' }).relation, '');
eq('超长名字截断', C.fixItem({ name: 'a'.repeat(80) }).name.length, 40);
ok('控制字符被清掉', C.fixItem({ name: 'a\u0000b' }).name === 'ab');

console.log('=== 2. 基本推算（今天 = 2026-09-10）===');
const mid = C.fixItem({ kind: 'festival', name: '中秋节', dateType: 'lunar', month: 8, day: 15, repeat: 'year' });
const mi = C.info(mid, T);
eq('中秋 下一次公历', `${mi.y}-${String(mi.m).padStart(2, '0')}-${String(mi.d).padStart(2, '0')}`, '2026-09-25');
eq('中秋 倒计时', mi.diff, 15);
eq('中秋 星期', L.WEEK_CN[mi.wd], '五');
eq('中秋 农历文本', L.lunarText(mi.lun.month, mi.lun.day, mi.lun.isLeap), '八月十五');

const nat = C.fixItem({ kind: 'festival', name: '国庆', dateType: 'solar', month: 10, day: 1, repeat: 'year' });
eq('国庆 倒计时', C.info(nat, T).diff, 21);

const past = C.fixItem({ kind: 'festival', name: '雷锋日', dateType: 'solar', month: 3, day: 5, repeat: 'year' });
const pi = C.info(past, T);
eq('已过去的公历日 -> 明年', pi.y, 2027);
eq('已过去 上次发生年份', L.numToYmd(pi.prevNum).y, 2026);

console.log('=== 3. 生日年龄 ===');
const mom = C.fixItem({ kind: 'birthday', name: '妈妈', dateType: 'lunar', month: 8, day: 15, year: 1962, repeat: 'year' });
const momI = C.info(mom, T);
eq('农历生日 周岁', momI.age, 2026 - 1962);
const dad = C.fixItem({ kind: 'birthday', name: '老爸', dateType: 'solar', month: 11, day: 3, year: 1960, repeat: 'year' });
eq('公历生日 周岁', C.info(dad, T).age, 2026 - 1960);
eq('非生日类型不显示年龄', C.info(C.fixItem({ kind: 'countdown', name: 'x', dateType: 'solar', month: 11, day: 3, year: 1960 }), T).age, null);

console.log('=== 4. 农历三十遇小月（除夕）===');
let smallYear = null;
for (let y = 2026; y <= 2060; y++) if (L.monthDays(y, 12) < 30) { smallYear = y; break; }
console.log(`  （腊月只有廿九的年份：${smallYear}）`);
const chuxi = C.fixItem({ kind: 'festival', name: '除夕', dateType: 'lunar', month: 12, day: 30, repeat: 'year' });
const t0 = L.ymdToNum(smallYear, 1, 1);
const ci = C.info(chuxi, t0);
ok('除夕在小月年份仍有日期', ci != null);
eq('除夕落在廿九', ci.lun.day, 29);
ok('除夕 = 次年春节前一天', ci.n === L.ymdToNum(...Object.values(L.lunarToSolar(smallYear + 1, 1, 1, false))) - 1);
ok('lunarDayMayShift 能识别三十会顺延', C.lunarDayMayShift(12, 30) === true);

console.log('=== 5. 提醒生成 ===');
const sf = C.fixItem({ kind: 'festival', name: '春节', dateType: 'lunar', month: 1, day: 1, repeat: 'year', remind: [15, 7, 1, 0] });
const st = C.defaultSettings({ defaultRemind: [7, 1, 0], remindTime: '09:00' });
// 从 2026-11-01 看，2027 春节是 2027-02-06，正好在 120 天窗口边缘
const t1 = L.ymdToNum(2026, 11, 1);
const r1 = C.buildReminders([sf], st, t1, new Date(2026, 10, 1, 8, 0).getTime());
const sfDay = L.nextOccurrence(sf, t1, 'year', true);
console.log(`  春节 2027 = ${JSON.stringify(L.numToYmd(sfDay))}，生成提醒 ${r1.length} 条`);
ok('提醒数量 = 4（15/7/1/0 各一条）', r1.length === 4, 'got ' + r1.length);
ok('提醒按时间升序', r1.every((r, k) => k === 0 || r1[k - 1].at <= r.at));
// 校验第一条（提前 15 天）的日期
const firstDay = L.numToYmd(sfDay - 15);
const firstAt = new Date(r1[0].at);
eq('提前15天的公历日', `${firstAt.getFullYear()}-${firstAt.getMonth() + 1}-${firstAt.getDate()}`, `${firstDay.y}-${firstDay.m}-${firstDay.d}`);
eq('提醒时间是 09:00', `${firstAt.getHours()}:${String(firstAt.getMinutes()).padStart(2, '0')}`, '9:00');
eq('id 带提前天数后缀', r1[0].id, sf.id + '#15');
ok('标题非空', r1[0].title.length > 0, r1[0].title);
ok('正文含农历与星期', r1[0].text.includes('农历') && r1[0].text.includes('周'), r1[0].text);

console.log('=== 6. 提醒边界 ===');
// 当天提醒：如果"今天"就是春节，且提醒时间还没过，应该生成今天的提醒
const sfDayNum = L.nextOccurrence(sf, T, 'year', true);
const todayRemind = C.buildReminders([sf], st, sfDayNum, new Date(L.numToYmd(sfDayNum).y, L.numToYmd(sfDayNum).m - 1, L.numToYmd(sfDayNum).d, 7, 0).getTime());
ok('当天 0 天提醒会生成', todayRemind.some(r => r.id === sf.id + '#0'));
// 提醒时间已过 -> 不生成
const late = C.buildReminders([sf], st, sfDayNum, new Date(L.numToYmd(sfDayNum).y, L.numToYmd(sfDayNum).m - 1, L.numToYmd(sfDayNum).d, 23, 0).getTime());
ok('提醒时间已过则跳过', !late.some(r => r.id === sf.id + '#0'));
// 超过 120 天的不排
const far = C.fixItem({ kind: 'countdown', name: '很远', dateType: 'solar', month: 1, day: 1, year: 2030, repeat: 'once', remind: [0] });
eq('超窗口不生成提醒', C.buildReminders([far], st, T, NOW).length, 0);
// 关闭提醒总开关
eq('关闭总开关后为空', C.buildReminders([sf], C.defaultSettings({ notifyOn: false }), t1, NOW).length, 0);
// 无 remind 时用默认
const noRemind = C.fixItem({ kind: 'festival', name: '国庆', dateType: 'solar', month: 10, day: 1, repeat: 'year' });
const r2 = C.buildReminders([noRemind], st, T, NOW);
ok('未单独设置时用默认提醒天数', r2.length === 3, 'got ' + r2.length);
eq('上限截断为 200', C.buildReminders(
  Array.from({ length: 60 }, (_, k) => C.fixItem({
    kind: 'festival', name: 'F' + k, dateType: 'solar', month: 1 + (k % 12), day: 1 + (k % 28), repeat: 'year', remind: [30, 15, 7, 3, 2, 1, 0]
  })), st, T, NOW).length <= 200, true);

console.log('=== 7. 排序 ===');
const many = [
  C.fixItem({ name: '丙', dateType: 'solar', month: 12, day: 1, repeat: 'year' }),
  C.fixItem({ name: '甲', dateType: 'solar', month: 9, day: 20, repeat: 'year' }),
  C.fixItem({ name: '乙', dateType: 'solar', month: 10, day: 5, repeat: 'year', pinned: true })
];
const rows = C.sortByDate(C.decorate(many, T));
eq('置顶排在最前', rows[0].it.name, '乙');
eq('其余按临近排序', rows[1].it.name, '甲');
eq('按名字排序（拼音：丙 bǐng < 甲 jiǎ < 乙 yǐ）', C.sortByName(C.decorate(many, T)).map(r => r.it.name), ['丙', '甲', '乙']);

console.log('=== 7.5 一次性的日子过期后不消失 ===');
const onceItem = C.fixItem({ kind: 'countdown', name: '去年体检', dateType: 'solar', month: 4, day: 8, year: 2025, repeat: 'once', remind: [7, 1, 0] });
const oi = C.info(onceItem, T);
ok('过期的一次性日子仍能取到信息', oi != null);
eq('标记为已过去', oi.passed, true);
eq('倒计时为负数', oi.diff < 0, true);
eq('已过去天数正确', oi.diff, L.ymdToNum(2025, 4, 8) - T);
eq('过期后不再排提醒', C.buildReminders([onceItem], st, T, NOW).length, 0);
eq('过期后仍出现在列表里', C.decorate([onceItem], T).length, 1);
const sortedPast = C.sortByDate(C.decorate([onceItem, nat], T));
eq('已过去的排在正常日子后面', sortedPast[sortedPast.length - 1].it.name, '去年体检');
const futureOnce = C.fixItem({ kind: 'countdown', name: '明年体检', dateType: 'solar', month: 4, day: 8, year: 2027, repeat: 'once', remind: [7, 1, 0] });
eq('未过期的一次性日子 passed=false', C.info(futureOnce, T).passed, false);

console.log('=== 8. 文案 ===');
eq('diffText 今天', C.diffText(0).main, '今天');
eq('diffText 后天', C.diffText(2).main, '后天');
eq('diffText 数字', C.diffText(12).main + C.diffText(12).sub, '12天后');
eq('sinceText 天', C.sinceText(5), '5 天前');
eq('sinceText 月', C.sinceText(75), '2 个月前');
eq('sinceText 年', C.sinceText(400), '1 年 1 个月前');
eq('ruleText 公历每年', C.ruleText(C.fixItem({ name: 'x', dateType: 'solar', month: 3, day: 8 })), '每年 3月8日');
eq('ruleText 农历每年', C.ruleText(C.fixItem({ name: 'x', dateType: 'lunar', month: 8, day: 15 })), '每年 农历八月十五');
eq('ruleText 农历一次性', C.ruleText(C.fixItem({ name: 'x', dateType: 'lunar', month: 8, day: 15, year: 2027, repeat: 'once' })), '2027年 农历八月十五');

console.log('=== 9. 备份往返 ===');
const st2 = C.normalizeStore({ items: C.sampleItems(), settings: { theme: 'dark', remindTime: '08:30' } });
const txt = C.exportPayload(st2);
const parsed = C.parseBackup(txt);
ok('导出可被解析', parsed.ok === true, parsed.msg);
eq('条数一致', parsed.count, st2.items.length);
eq('主题保留', parsed.store.settings.theme, 'dark');
eq('提醒时间保留', parsed.store.settings.remindTime, '08:30');
eq('解析垃圾文本失败', C.parseBackup('not json').ok, false);
eq('解析空对象失败', C.parseBackup('{}').ok, false);
eq('解析空 items 失败', C.parseBackup('{"items":[]}').ok, false);
const merged = C.mergeItems(st2.items, [C.fixItem({ id: st2.items[0].id, name: '改过名', kind: 'birthday' })]);
eq('合并去重', merged.length, st2.items.length);
eq('合并以后者为准', merged[0].name, '改过名');

console.log('=== 10. 示例数据与预设 ===');
const sample = C.sampleItems();
eq('示例条数', sample.length, 6);
ok('示例每条都能推算出日期', sample.every(it => C.info(it, T) != null),
  JSON.stringify(sample.filter(it => C.info(it, T) == null).map(it => it.name)));
const presets = C.presetItems('all');
eq('预设条数', presets.length, C.PRESET_LUNAR.length + C.PRESET_SOLAR.length);
ok('预设每条都能推算出日期', presets.every(it => C.info(it, T) != null),
  JSON.stringify(presets.filter(it => C.info(it, T) == null).map(it => it.name)));
ok('闰月探测：2025 有闰六月', C.hasLeapNear(6, 2025, 1) === true);
ok('闰月探测：2026 无闰月', C.hasLeapNear(9, 2026, 1) === false);

console.log('=== 11. 分享文本 ===');
const share = C.shareText(sample, T);
ok('分享文本含标题', share.includes('好日子'));
ok('分享文本含人名', share.includes('妈妈'));
ok('分享文本含分组', share.includes('▍'));
ok('空数据也能分享', C.shareText([], T).includes('还没有记录'));

console.log('=== 12. 全量压力：示例数据在 2026-2100 抽样日都不崩 ===');
let crashes = 0, nulls = 0, checked = 0;
for (let y = 2026; y <= 2100; y += 3) {
  for (const m of [1, 4, 7, 10]) {
    const tt = L.ymdToNum(y, m, 1);
    try {
      sample.concat(presets).forEach(it => {
        if (it.repeat === 'once') return;   // 一次性日子的过去态由 7.5 单独覆盖
        checked++;
        if (C.info(it, tt) == null) { nulls++; console.log('  ✗ 推算为空: ' + it.name + ' @' + y + '-' + m); }
      });
      C.buildReminders(sample, st, tt, new Date(y, m - 1, 1, 0, 0).getTime());
      C.shareText(sample, tt);
    } catch (e) { crashes++; console.log('  ✗ ' + y + '-' + m + ': ' + e.message); }
  }
}
eq('无异常抛出', crashes, 0);
eq('无推算失败', nulls, 0);
ok('确实跑了足量样本', checked > 1500, 'checked=' + checked);

console.log(`\n结果: ${pass} 通过 / ${fail} 失败`);
process.exit(fail ? 1 : 0);
