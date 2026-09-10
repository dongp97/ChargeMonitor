/*!
 * 农历 / 节气 / 干支 —— 纯算法，无依赖
 * 支持范围：1900-01-31 ~ 2100-12-31
 * 内部统一用「日序数」(day number) 做日历运算，规避时区与闰秒问题。
 * 基准：day 0 = 1900-01-31（农历 1900 年正月初一，星期三）
 */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.Lunar = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  var BASE_MS = Date.UTC(1900, 0, 31);
  var DAY_MS = 86400000;

  // 1900-2100 年农历数据（每年一个 20bit 整数）
  // bit16 闰月大小(1=30天) | bit15..4 十二个月大小(1=30天) | bit3..0 闰月月份(0=无)
  var LUNAR_INFO = [
    0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2, // 1900-1909
    0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977, // 1910-1919
    0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970, // 1920-1929
    0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950, // 1930-1939
    0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557, // 1940-1949
    0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5b0, 0x14573, 0x052b0, 0x0a9a8, 0x0e950, 0x06aa0, // 1950-1959
    0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0, // 1960-1969
    0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b6a0, 0x195a6, // 1970-1979
    0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570, // 1980-1989
    0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x055c0, 0x0ab60, 0x096d5, 0x092e0, // 1990-1999
    0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5, // 2000-2009
    0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930, // 2010-2019
    0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530, // 2020-2029
    0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45, // 2030-2039
    0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0, // 2040-2049
    0x14b63, 0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06b20, 0x1a6c4, 0x0aae0, // 2050-2059
    0x0a2e0, 0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4, // 2060-2069
    0x052d0, 0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0, // 2070-2079
    0x0b273, 0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160, // 2080-2089
    0x0e968, 0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a2d0, 0x0d150, 0x0f252, // 2090-2099
    0x0d520 // 2100
  ];

  var MIN_YEAR = 1900;
  var MAX_YEAR = 2100;

  var GAN = ['甲', '乙', '丙', '丁', '戊', '己', '庚', '辛', '壬', '癸'];
  var ZHI = ['子', '丑', '寅', '卯', '辰', '巳', '午', '未', '申', '酉', '戌', '亥'];
  var ANIMAL = ['鼠', '牛', '虎', '兔', '龙', '蛇', '马', '羊', '猴', '鸡', '狗', '猪'];
  var MONTH_CN = ['正', '二', '三', '四', '五', '六', '七', '八', '九', '十', '冬', '腊'];
  var WEEK_CN = ['日', '一', '二', '三', '四', '五', '六'];
  var SOLAR_MONTH_CN = ['一', '二', '三', '四', '五', '六', '七', '八', '九', '十', '十一', '十二'];
  var SOLAR_DAY_CN = ['一', '二', '三', '四', '五', '六', '七', '八', '九', '十'];
  var TERM_NAMES = ['小寒', '大寒', '立春', '雨水', '惊蛰', '春分', '清明', '谷雨', '立夏', '小满', '芒种', '夏至',
    '小暑', '大暑', '立秋', '处暑', '白露', '秋分', '寒露', '霜降', '立冬', '小雪', '大雪', '冬至'];
  // 节气近似公式系数（1900-2100 可用）
  var TERM_C = [0, 21208, 42467, 63836, 85337, 107014, 128867, 150921, 173149, 195551, 218072, 240693,
    263343, 285989, 308563, 331033, 353350, 375494, 397447, 419210, 440795, 462224, 483532, 504758];

  var CONSTELLATIONS = ['摩羯座', '水瓶座', '双鱼座', '白羊座', '金牛座', '双子座', '巨蟹座',
    '狮子座', '处女座', '天秤座', '天蝎座', '射手座'];
  // 星座交界日（每月第几天开始属于下一个星座，取西洋占星的常见口径）
  var CONST_EDGE = [20, 19, 21, 20, 21, 22, 23, 23, 23, 24, 23, 22];

  // ---------------------------------------------------------------- 基础工具

  /** 公历 -> 日序数（day 0 = 1900-01-31） */
  function ymdToNum(y, m, d) {
    return Math.round((Date.UTC(y, m - 1, d) - BASE_MS) / DAY_MS);
  }

  /** 日序数 -> 公历 */
  function numToYmd(n) {
    var dt = new Date(BASE_MS + n * DAY_MS);
    return { y: dt.getUTCFullYear(), m: dt.getUTCMonth() + 1, d: dt.getUTCDate() };
  }

  /** 今天（本地日历日）的日序数 */
  function todayNum() {
    var t = new Date();
    return ymdToNum(t.getFullYear(), t.getMonth() + 1, t.getDate());
  }

  /** 日序数 -> 星期（0=周日）  1900-01-31 是星期三 */
  function weekdayOfNum(n) { return ((n + 3) % 7 + 7) % 7; }

  function pad2(v) { return (v < 10 ? '0' : '') + v; }

  function fmtDate(y, m, d) { return y + '-' + pad2(m) + '-' + pad2(d); }

  // ---------------------------------------------------------------- 农历核心

  /** 农历 y 年的闰月月份，无闰月返回 0 */
  function leapMonth(y) {
    if (y < MIN_YEAR || y > MAX_YEAR) return 0;
    return LUNAR_INFO[y - MIN_YEAR] & 0xf;
  }

  /** 农历 y 年闰月的天数（无闰月返回 0） */
  function leapDays(y) {
    if (!leapMonth(y)) return 0;
    return (LUNAR_INFO[y - MIN_YEAR] & 0x10000) ? 30 : 29;
  }

  /** 农历 y 年 m 月的天数（m: 1-12，指非闰月） */
  function monthDays(y, m) {
    if (y < MIN_YEAR || y > MAX_YEAR) return 29;
    return (LUNAR_INFO[y - MIN_YEAR] & (0x10000 >> m)) ? 30 : 29;
  }

  /** 农历 y 年总天数 */
  function yearDays(y) {
    var sum = 348; // 12 * 29
    for (var i = 0x8000; i > 0x8; i >>= 1) sum += (LUNAR_INFO[y - MIN_YEAR] & i) ? 1 : 0;
    return sum + leapDays(y);
  }

  /** 公历 -> 农历 */
  function solarToLunar(y, m, d) {
    var offset = ymdToNum(y, m, d);
    if (offset < 0) return null;
    var yIdx = MIN_YEAR;
    var temp = 0;
    for (; yIdx <= MAX_YEAR && offset > 0; yIdx++) {
      temp = yearDays(yIdx);
      offset -= temp;
    }
    if (offset < 0) { offset += temp; yIdx--; }
    var ly = yIdx;
    if (ly > MAX_YEAR) return null;

    var leap = leapMonth(ly);
    var isLeap = false;
    var mIdx;
    for (mIdx = 1; mIdx < 13 && offset > 0; mIdx++) {
      if (leap > 0 && mIdx === leap + 1 && isLeap === false) {
        --mIdx;
        isLeap = true;
        temp = leapDays(ly);
      } else {
        temp = monthDays(ly, mIdx);
      }
      if (isLeap === true && mIdx === leap + 1) isLeap = false;
      offset -= temp;
    }
    if (offset === 0 && leap > 0 && mIdx === leap + 1) {
      if (isLeap) { isLeap = false; } else { isLeap = true; --mIdx; }
    }
    if (offset < 0) { offset += temp; --mIdx; }
    return { year: ly, month: mIdx, day: offset + 1, isLeap: isLeap };
  }

  /** 农历 -> 公历（返回 {y,m,d}） */
  function lunarToSolar(ly, lm, ld, isLeap) {
    if (ly < MIN_YEAR || ly > MAX_YEAR) return null;
    if (lm < 1 || lm > 12) return null;
    var maxD = (isLeap && leapMonth(ly) === lm) ? leapDays(ly) : monthDays(ly, lm);
    if (ld < 1 || ld > maxD) return null;

    var offset = 0;
    for (var y = MIN_YEAR; y < ly; y++) offset += yearDays(y);
    var leap = leapMonth(ly);
    for (var m = 1; m < lm; m++) {
      offset += monthDays(ly, m);
      if (leap === m) offset += leapDays(ly);
    }
    if (isLeap && leap === lm) offset += monthDays(ly, lm);
    offset += ld - 1;

    var r = numToYmd(offset);
    return { y: r.y, m: r.m, d: r.d };
  }

  // ---------------------------------------------------------------- 节气

  /** 第 n 个节气（0=小寒）在 y 年的公历日（月由 n 推出：n/2+1） */
  function termDay(y, n) {
    var ms = 31556925974.7 * (y - 1900) + TERM_C[n] * 60000 + Date.UTC(1900, 0, 6, 2, 5);
    return new Date(ms).getUTCDate();
  }

  /** 某天的节气名，没有则返回 null */
  function solarTerm(y, m, d) {
    var n = (m - 1) * 2;
    if (termDay(y, n) === d) return TERM_NAMES[n];
    if (termDay(y, n + 1) === d) return TERM_NAMES[n + 1];
    return null;
  }

  /** 列出 y 年某月的节气 [{day, name}] */
  function termsOfMonth(y, m) {
    var out = [];
    var n = (m - 1) * 2;
    var d1 = termDay(y, n), d2 = termDay(y, n + 1);
    out.push({ day: d1, name: TERM_NAMES[n] });
    out.push({ day: d2, name: TERM_NAMES[n + 1] });
    return out;
  }

  // ---------------------------------------------------------------- 干支 / 生肖

  /** 年干支（以农历年计） */
  function ganzhiYear(ly) {
    var i = (ly - 4) % 60;
    if (i < 0) i += 60;
    return GAN[i % 10] + ZHI[i % 12];
  }

  function animalOf(ly) {
    var i = (ly - 4) % 12;
    if (i < 0) i += 12;
    return ANIMAL[i];
  }

  /** 日干支（按公历日序数，基准 1900-01-31 为甲辰日） */
  function ganzhiDay(n) {
    var i = ((n + 40) % 60 + 60) % 60; // 1900-01-31 = 甲辰(第41个, index 40)
    return GAN[i % 10] + ZHI[i % 12];
  }

  function ganzhiMonth(y, m) {
    // 简化：按节气分月的月柱，仅用于展示
    var i = ((y - 1900) * 12 + m + 12) % 60;
    return GAN[i % 10] + ZHI[i % 12];
  }

  // ---------------------------------------------------------------- 星座 / 文本

  function constellation(m, d) {
    var idx = m - 1;
    return d < CONST_EDGE[idx] ? CONSTELLATIONS[idx] : CONSTELLATIONS[(idx + 1) % 12];
  }

  function lunarDayName(d) {
    if (d === 10) return '初十';
    if (d === 20) return '二十';
    if (d === 30) return '三十';
    var p = ['初', '十', '廿', '三'][Math.floor(d / 10)];
    var u = SOLAR_DAY_CN[(d % 10) - 1];
    return p + u;
  }

  function lunarMonthName(m) { return MONTH_CN[m - 1]; }

  /** 农历完整文本，如「八月十五」／「闰六月初三」 */
  function lunarText(month, day, isLeap) {
    var md = lunarDayName(day);
    if (day === 1) return (isLeap ? '闰' : '') + lunarMonthName(month) + '月';
    return (isLeap ? '闰' : '') + lunarMonthName(month) + '月' + md;
  }

  /** 简短农历文本：初一/十五只显示月，其余显示日 —— 用于日历格小字 */
  function lunarShort(l) {
    if (!l) return '';
    if (l.day === 1) return (l.isLeap ? '闰' : '') + lunarMonthName(l.month) + '月';
    return lunarDayName(l.day);
  }

  function solarText(y, m, d) {
    return y + '年' + SOLAR_MONTH_CN[m - 1] + '月' + (d < 11 ? SOLAR_DAY_CN[d - 1] : d + '') + '日';
  }

  // ---------------------------------------------------------------- 日程推算

  /**
   * 推算下一个发生的公历日序数。
   * @param {object} spec {dateType:'solar'|'lunar', month, day, isLeap, year}
   * @param {number} fromNum 起始日序数（含当天）
   * @param {string} repeat 'year' | 'once'
   * @param {boolean} includeToday 是否把「就是今天」算作命中
   * @returns {number|null} 日序数
   */
  function nextOccurrence(spec, fromNum, repeat, includeToday) {
    if (fromNum == null) fromNum = todayNum();
    var start = fromNum + (includeToday === false ? 1 : 0);
    var isLunar = spec.dateType === 'lunar';

    if (repeat === 'once') {
      var n0 = isLunar
        ? numOfLunar(spec.year, spec.month, spec.day, spec.isLeap)
        : ymdToNum(spec.year, spec.month, spec.day);
      return (n0 != null && n0 >= start) ? n0 : null;
    }

    var baseY = numToYmd(start).y;
    for (var k = 0; k <= 3; k++) {
      var y = baseY + k;
      var n = isLunar
        ? numOfLunarLenient(y, spec.month, spec.day, spec.isLeap)
        : ymdToNum(y, spec.month, spec.day);
      if (n != null && n >= start) return n;
    }
    return null;
  }

  /** 农历日期在「农历年 ly」里对应的日序数 */
  function numOfLunar(ly, lm, ld, isLeap) {
    var s = lunarToSolar(ly, lm, ld, isLeap);
    return s ? ymdToNum(s.y, s.m, s.d) : null;
  }

  /**
   * 宽松版：农历三十遇到小月（该月只有廿九）时落到当月最后一天。
   * 民间「廿九当三十过」，除夕尤其常见 —— 不这样处理，
   * 腊月三十这类生日会在小月年份直接从列表里消失。
   */
  function numOfLunarLenient(ly, lm, ld, isLeap) {
    var n = numOfLunar(ly, lm, ld, isLeap);
    if (n != null) return n;
    var max = (isLeap && leapMonth(ly) === lm) ? leapDays(ly) : monthDays(ly, lm);
    if (max > 0 && ld > max) return numOfLunar(ly, lm, max, isLeap);
    return null;
  }

  /** 该农历日在该农历年是否真实存在（false = 小月，按宽松规则落到月末） */
  function lunarDayExists(ly, lm, ld, isLeap) {
    return numOfLunar(ly, lm, ld, isLeap) != null;
  }

  /**
   * 某条日程「上一次发生」的日序数（用于计算已过去多久）
   */
  function prevOccurrence(spec, fromNum, repeat) {
    if (fromNum == null) fromNum = todayNum();
    var isLunar = spec.dateType === 'lunar';
    if (repeat === 'once') {
      var n0 = isLunar
        ? numOfLunar(spec.year, spec.month, spec.day, spec.isLeap)
        : ymdToNum(spec.year, spec.month, spec.day);
      return (n0 != null && n0 <= fromNum) ? n0 : null;
    }
    var baseY = numToYmd(fromNum).y;
    for (var k = 0; k <= 3; k++) {
      var y = baseY - k;
      var n = isLunar
        ? numOfLunarLenient(y, spec.month, spec.day, spec.isLeap)
        : ymdToNum(y, spec.month, spec.day);
      if (n != null && n <= fromNum) return n;
    }
    return null;
  }

  /** 距离文本 */
  function relativeText(diff) {
    if (diff === 0) return '今天';
    if (diff === 1) return '明天';
    if (diff === 2) return '后天';
    if (diff === -1) return '昨天';
    if (diff > 0) return diff + ' 天后';
    return Math.abs(diff) + ' 天前';
  }

  return {
    MIN_YEAR: MIN_YEAR,
    MAX_YEAR: MAX_YEAR,
    GAN: GAN, ZHI: ZHI, ANIMAL: ANIMAL, TERM_NAMES: TERM_NAMES, WEEK_CN: WEEK_CN,
    ymdToNum: ymdToNum,
    numToYmd: numToYmd,
    todayNum: todayNum,
    weekdayOfNum: weekdayOfNum,
    pad2: pad2,
    fmtDate: fmtDate,
    leapMonth: leapMonth,
    leapDays: leapDays,
    monthDays: monthDays,
    yearDays: yearDays,
    solarToLunar: solarToLunar,
    lunarToSolar: lunarToSolar,
    numOfLunar: numOfLunar,
    numOfLunarLenient: numOfLunarLenient,
    lunarDayExists: lunarDayExists,
    solarTerm: solarTerm,
    termsOfMonth: termsOfMonth,
    ganzhiYear: ganzhiYear,
    ganzhiDay: ganzhiDay,
    animalOf: animalOf,
    constellation: constellation,
    lunarDayName: lunarDayName,
    lunarMonthName: lunarMonthName,
    lunarText: lunarText,
    lunarShort: lunarShort,
    solarText: solarText,
    nextOccurrence: nextOccurrence,
    prevOccurrence: prevOccurrence,
    relativeText: relativeText
  };
});
