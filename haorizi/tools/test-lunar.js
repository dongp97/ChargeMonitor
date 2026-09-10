const L = require('../app/src/main/assets/lunar.js');

let pass = 0, fail = 0;
function eq(label, got, want) {
  const ok = got === want;
  if (ok) pass++; else { fail++; console.log(`  ✗ ${label}: got ${got}  want ${want}`); }
}

console.log('=== 1. 春节（正月初一）公历日期 ===');
const springFestival = {
  1900: '01-31', 1912: '02-18', 1920: '02-20', 1930: '01-30', 1940: '02-08', 1950: '02-17',
  1960: '01-28', 1970: '02-06', 1980: '02-16', 1990: '01-27', 1991: '02-15', 1992: '02-04',
  1993: '01-23', 1994: '02-10', 1995: '01-31', 1996: '02-19', 1997: '02-07', 1998: '01-28',
  1999: '02-16', 2000: '02-05', 2001: '01-24', 2002: '02-12', 2003: '02-01', 2004: '01-22',
  2005: '02-09', 2006: '01-29', 2007: '02-18', 2008: '02-07', 2009: '01-26', 2010: '02-14',
  2011: '02-03', 2012: '01-23', 2013: '02-10', 2014: '01-31', 2015: '02-19', 2016: '02-08',
  2017: '01-28', 2018: '02-16', 2019: '02-05', 2020: '01-25', 2021: '02-12', 2022: '02-01',
  2023: '01-22', 2024: '02-10', 2025: '01-29', 2026: '02-17', 2027: '02-06', 2028: '01-26',
  2029: '02-13', 2030: '02-03', 2031: '01-23', 2032: '02-11', 2033: '01-31', 2034: '02-19',
  2035: '02-08', 2036: '01-28', 2037: '02-15', 2038: '02-04', 2039: '01-24', 2040: '02-12',
  2050: '01-23', 2060: '02-02', 2061: '01-21', 2064: '02-17', 2069: '01-23', 2070: '02-11',
  2079: '02-02', 2080: '01-22', 2084: '02-06', 2090: '01-30', 2096: '01-25', 2099: '01-21',
  2100: '02-09'
};
for (const [y, md] of Object.entries(springFestival)) {
  const p = L.lunarToSolar(+y, 1, 1, false);
  const got = p ? L.fmtDate(p.y, p.m, p.d).slice(5) : 'null';
  eq(`春节 ${y}`, got, md);
}

console.log('=== 2. 已知农历节日 ===');
const known = [
  ['2026-09-25', [2026, 8, 15, false], '2026 中秋'],
  ['2025-10-06', [2025, 8, 15, false], '2025 中秋'],
  ['2024-09-17', [2024, 8, 15, false], '2024 中秋'],
  ['2025-05-31', [2025, 5, 5, false], '2025 端午'],
  ['2026-06-19', [2026, 5, 5, false], '2026 端午'],
  ['2026-03-03', [2026, 1, 15, false], '2026 元宵'],
  ['2025-02-12', [2025, 1, 15, false], '2025 元宵'],
  ['2026-08-19', [2026, 7, 7, false], '2026 七夕'],
  ['2026-09-11', [2026, 8, 1, false], '农历八月初一 2026']
];
for (const [solar, [ly, lm, ld, leap], label] of known) {
  const p = L.lunarToSolar(ly, lm, ld, leap);
  eq(label + ' →公历', p ? L.fmtDate(p.y, p.m, p.d) : 'null', solar);
  const [y, m, d] = solar.split('-').map(Number);
  const lb = L.solarToLunar(y, m, d);
  eq(label + ' →农历', lb ? `${lb.year}/${lb.month}/${lb.day}/${lb.isLeap}` : 'null', `${ly}/${lm}/${ld}/${leap}`);
}

console.log('=== 3. 闰月 ===');
const leaps = [
  [2020, 4, '2020-05-23'], [2023, 2, '2023-03-22'], [2025, 6, '2025-07-25'],
  [2017, 6, '2017-07-23'], [2012, 4, '2012-05-21'], [2028, 5, '2028-06-23']
];
for (const [y, m, solar] of leaps) {
  eq(`闰月表 ${y}`, L.leapMonth(y), m);
  const p = L.lunarToSolar(y, m, 1, true);
  eq(`${y} 闰${m}月初一`, p ? L.fmtDate(p.y, p.m, p.d) : 'null', solar);
}
eq('2024 无闰月', L.leapMonth(2024), 0);
eq('2026 无闰月', L.leapMonth(2026), 0);

console.log('=== 4. 公历↔农历 全量往返一致性 (1900-2100 每年抽查) ===');
let rt = 0, rtBad = 0;
for (let n = 0; n < 73000; n += 7) {
  const s = L.numToYmd(n);
  if (s.y > 2100) break;
  const l = L.solarToLunar(s.y, s.m, s.d);
  if (!l) { rtBad++; continue; }
  const back = L.lunarToSolar(l.year, l.month, l.day, l.isLeap);
  rt++;
  if (!back || back.y !== s.y || back.m !== s.m || back.d !== s.d) {
    rtBad++;
    if (rtBad <= 5) console.log(`  ✗ 往返失败 ${s.y}-${s.m}-${s.d} -> ${JSON.stringify(l)} -> ${JSON.stringify(back)}`);
  }
}
eq(`往返一致 (共 ${rt} 个采样点)`, rtBad, 0);

console.log('=== 5. 农历日连续性（每月初一必须接上月最后一天） ===');
let contBad = 0, cont = 0;
for (let y = 1900; y <= 2100; y++) {
  const leap = L.leapMonth(y);
  const seq = [];
  for (let m = 1; m <= 12; m++) {
    seq.push([m, false, L.monthDays(y, m)]);
    if (leap === m) seq.push([m, true, L.leapDays(y)]);
  }
  let prevEnd = null;
  for (const [m, lp, days] of seq) {
    const first = L.lunarToSolar(y, m, 1, lp);
    const last = L.lunarToSolar(y, m, days, lp);
    if (!first || !last) { contBad++; continue; }
    const fn = L.ymdToNum(first.y, first.m, first.d);
    const ln = L.ymdToNum(last.y, last.m, last.d);
    cont++;
    if (ln - fn !== days - 1) contBad++;
    if (prevEnd !== null && fn !== prevEnd + 1) contBad++;
    prevEnd = ln;
  }
}
eq(`农历月连续性 (共 ${cont} 个月)`, contBad, 0);

console.log('=== 6. 干支 / 生肖 ===');
eq('2026 年干支', L.ganzhiYear(2026), '丙午');
eq('2026 生肖', L.animalOf(2026), '马');
eq('2025 年干支', L.ganzhiYear(2025), '乙巳');
eq('2025 生肖', L.animalOf(2025), '蛇');
eq('2024 年干支', L.ganzhiYear(2024), '甲辰');
eq('1984 年干支', L.ganzhiYear(1984), '甲子');
eq('2000 年干支', L.ganzhiYear(2000), '庚辰');
eq('1900-01-31 日干支', L.ganzhiDay(L.ymdToNum(1900, 1, 31)), '甲辰');

console.log('=== 7. 节气 ===');
const terms = [
  [2026, 2, 4, '立春'], [2026, 4, 5, '清明'], [2026, 6, 21, '夏至'],
  [2026, 12, 22, '冬至'], [2025, 2, 3, '立春'], [2025, 4, 4, '清明'],
  [2025, 12, 21, '冬至'], [2020, 4, 4, '清明'], [2010, 2, 4, '立春'],
  [2000, 3, 5, '惊蛰'], [2026, 3, 5, '惊蛰'], [2026, 8, 7, '立秋']
];
for (const [y, m, d, name] of terms) {
  eq(`节气 ${y}-${m}-${d}`, L.solarTerm(y, m, d), name);
}

console.log('=== 8. 星座 ===');
eq('9/10 星座', L.constellation(9, 10), '处女座');
eq('1/15 星座', L.constellation(1, 15), '摩羯座');
eq('12/25 星座', L.constellation(12, 25), '摩羯座');
eq('11/23 星座', L.constellation(11, 23), '射手座');

console.log('=== 9. 下一次发生推算 ===');
// 农历八月十五（中秋）从 2026-01-01 起下一次应为 2026-09-25
eq('中秋 下一次', L.fmtDate(...Object.values(L.numToYmd(
  L.nextOccurrence({ dateType: 'lunar', month: 8, day: 15 }, L.ymdToNum(2026, 1, 1), 'year')))).slice(0),
  '2026-09-25');
// 从 2026-09-26 起下一次中秋应为 2027-09-15
eq('中秋 跨年', L.fmtDate(...Object.values(L.numToYmd(
  L.nextOccurrence({ dateType: 'lunar', month: 8, day: 15 }, L.ymdToNum(2026, 9, 26), 'year')))),
  '2027-09-15');
// 公历生日 10-01 从 2026-09-10 起 = 2026-10-01
eq('公历 下一次', L.fmtDate(...Object.values(L.numToYmd(
  L.nextOccurrence({ dateType: 'solar', month: 10, day: 1 }, L.ymdToNum(2026, 9, 10), 'year')))),
  '2026-10-01');
// 包含今天
eq('含今天', L.nextOccurrence({ dateType: 'solar', month: 9, day: 10 }, L.ymdToNum(2026, 9, 10), 'year'),
  L.ymdToNum(2026, 9, 10));
// 一次性未来
eq('一次性未来', L.fmtDate(...Object.values(L.numToYmd(
  L.nextOccurrence({ dateType: 'solar', year: 2027, month: 3, day: 8 }, L.ymdToNum(2026, 9, 10), 'once')))),
  '2027-03-08');
eq('一次性已过', L.nextOccurrence({ dateType: 'solar', year: 2025, month: 3, day: 8 }, L.ymdToNum(2026, 9, 10), 'once'), null);

console.log('=== 10. 文本输出 ===');
eq('农历文本 8/15', L.lunarText(8, 15, false), '八月十五');
eq('农历文本 闰6/1', L.lunarText(6, 1, true), '闰六月');
eq('农历日 1', L.lunarDayName(1), '初一');
eq('农历日 20', L.lunarDayName(20), '二十');
eq('农历日 21', L.lunarDayName(21), '廿一');
eq('农历日 30', L.lunarDayName(30), '三十');
eq('农历日 11', L.lunarDayName(11), '十一');
eq('农历日 29', L.lunarDayName(29), '廿九');
eq('简短 初一', L.lunarShort({ month: 8, day: 1, isLeap: false }), '八月');
eq('简短 十五', L.lunarShort({ month: 8, day: 15, isLeap: false }), '十五');
eq('公历文本', L.solarText(2026, 9, 10), '2026年九月十日');

console.log('=== 11. 农历三十遇小月（廿九当三十）===');
let smallMonthYears = 0, chuxiOk = 0;
for (let y = 2026; y <= 2050; y++) {
  // 除夕 = 农历 y 年腊月最后一天 = 次年春节前一天
  const ny = L.lunarToSolar(y + 1, 1, 1, false);
  const nyNum = L.ymdToNum(ny.y, ny.m, ny.d);
  const lenient = L.numOfLunarLenient(y, 12, 30, false);
  eq(`除夕 ${y}`, lenient, nyNum - 1);
  if (lenient === nyNum - 1) chuxiOk++;
  if (!L.lunarDayExists(y, 12, 30, false)) smallMonthYears++;
}
eq('腊月三十存在小月情形（验证宽松规则确实被触发）', smallMonthYears > 0, true);
eq(`除夕全部正确 (${chuxiOk}/25)`, chuxiOk, 25);
// 小月年份：宽松版必须命中，严格版必须为 null
const sm = [];
for (let y = 2026; y <= 2060; y++) if (!L.lunarDayExists(y, 12, 30, false)) sm.push(y);
if (sm.length) {
  eq(`小月年份 ${sm[0]}：严格版为 null`, L.numOfLunar(sm[0], 12, 30, false), null);
  eq(`小月年份 ${sm[0]}：宽松版落月末`, L.numOfLunarLenient(sm[0], 12, 30, false) != null, true);
}
// 八月三十同理（生日常见）
let baYue = 0;
for (let y = 2026; y <= 2060; y++) {
  const n = L.nextOccurrence({ dateType: 'lunar', month: 8, day: 30 }, L.ymdToNum(y, 1, 1), 'year');
  if (n != null) baYue++;
}
eq('八月三十 60 年内每年都能推算出日期', baYue, 35);

console.log('=== 12. 边界 ===');
eq('1900-01-30 越界', L.solarToLunar(1900, 1, 30), null);
eq('2100-12-31 可用', L.solarToLunar(2100, 12, 31) ? 'ok' : 'null', 'ok');
eq('星期 1900-01-31', L.weekdayOfNum(L.ymdToNum(1900, 1, 31)), 3);
eq('星期 2026-09-10', L.weekdayOfNum(L.ymdToNum(2026, 9, 10)), 4);
eq('星期 2026-09-13', L.weekdayOfNum(L.ymdToNum(2026, 9, 13)), 0);

console.log(`\n结果: ${pass} 通过 / ${fail} 失败`);
process.exit(fail ? 1 : 0);
