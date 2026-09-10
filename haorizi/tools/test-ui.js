/**
 * 界面层冒烟测试：用 jsdom 真实加载 index.html + 三个脚本，再模拟点击走完主要流程。
 * 运行：node test-ui.js
 */
const fs = require('fs');
const path = require('path');
const { JSDOM, VirtualConsole } = require(path.join('C:/Users/dongp/.workbuddy/binaries/node/workspace/node_modules/jsdom'));

const ASSETS = path.resolve(__dirname, '../app/src/main/assets');

let pass = 0, fail = 0;
function ok(label, cond, extra) {
  if (cond) pass++; else { fail++; console.log(`  ✗ ${label}${extra !== undefined ? ' — ' + extra : ''}`); }
}
function eq(label, got, want) { ok(label, got === want, `got ${JSON.stringify(got)} want ${JSON.stringify(want)}`); }

const vc = new VirtualConsole();
const scriptErrors = [];
vc.on('jsdomError', e => scriptErrors.push(e.message));

const html = fs.readFileSync(path.join(ASSETS, 'index.html'), 'utf8');
const dom = new JSDOM(html, {
  runScripts: 'outside-only',
  url: 'https://appassets.androidplatform.net/assets/index.html',
  pretendToBeVisual: true,
  virtualConsole: vc
});
const win = dom.window, doc = win.document;

win.matchMedia = function () {
  return { matches: false, addEventListener() { }, removeEventListener() { }, addListener() { }, removeListener() { } };
};
win.scrollTo = function () { };

const bridge = { calls: [], reminders: null, lastExport: null, lastShare: null };
win.AndroidBridge = {
  info() { bridge.calls.push('info'); return JSON.stringify({ version: '1.0', sdk: 34, model: 'test', notifyGranted: true, exactAlarm: true }); },
  toast(m) { bridge.calls.push('toast:' + m); },
  vibrate() { bridge.calls.push('vibrate'); },
  setChrome(bg, l) { bridge.calls.push('chrome:' + bg + ':' + l); },
  setBackHandled(v) { bridge.calls.push('back:' + v); },
  requestNotify() { bridge.calls.push('requestNotify'); },
  testNotification() { bridge.calls.push('testNotification'); },
  openExactAlarmSettings() { bridge.calls.push('exactAlarm'); },
  syncReminders(j) { bridge.reminders = j; bridge.calls.push('sync'); },
  exportBackup(n, c) { bridge.lastExport = c; bridge.calls.push('export:' + n); return '已保存到「下载」目录：' + n; },
  shareText(t) { bridge.lastShare = t; bridge.calls.push('share'); },
  pickBackup() { bridge.calls.push('pick'); }
};

['lunar.js', 'core.js', 'app.js'].forEach(f => {
  win.eval(fs.readFileSync(path.join(ASSETS, f), 'utf8'));
});

const $ = s => doc.querySelector(s);
const $$ = s => Array.from(doc.querySelectorAll(s));
function click(el) {
  if (!el) throw new Error('click: 元素不存在');
  el.dispatchEvent(new win.MouseEvent('click', { bubbles: true, cancelable: true }));
}
function clickAct(act, id) {
  const sel = id === undefined ? `[data-act="${act}"]` : `[data-act="${act}"][data-id="${id}"]`;
  const all = $$(sel);
  if (!all.length) throw new Error('找不到 ' + sel);
  click(all[0]);
  return all[0];
}
const pageText = () => $('#page').textContent;
const tick = () => new Promise(r => setTimeout(r, 0));
const store = () => JSON.parse(win.localStorage.getItem('haorizi.v1'));
const sheetText = () => $('#sheet').textContent;

/** 在列表或主卡片里按名字找一行（置顶的排在列表最前，最近的会在主卡片上） */
function findRow(name) {
  const inList = $$('.item').find(e => e.querySelector('.item-n').textContent.includes(name));
  if (inList) return inList;
  const hero = $('.hero');
  if (hero && hero.textContent.includes(name)) return hero;
  return null;
}
function nameOf(el) {
  const n = el.querySelector('.item-n') || el.querySelector('.hero-name');
  return n.textContent.replace('★', '').trim();
}

(async function run() {

  console.log('=== 1. 启动与空状态 ===');
  ok('页面已渲染', $('#page').innerHTML.length > 100);
  ok('显示空状态引导', pageText().includes('先把重要的日子记下来'));
  ok('底部四个 Tab', $$('.tab').length === 4);
  eq('Tab 文案', $$('.tab').map(t => t.textContent.trim()).join('/'), '近期/全部/日历/设置');
  eq('空数据时同步了空提醒', bridge.reminders, '[]');
  ok('启动时把底色同步给原生', bridge.calls.some(c => c.startsWith('chrome:')));
  ok('启动时同步了返回键状态', bridge.calls.some(c => c === 'back:false'));

  console.log('=== 2. 载入示例数据 ===');
  clickAct('fpresets');
  await tick();
  ok('出现主卡片', !!$('.hero'));
  ok('主卡片有倒计时', !!$('.hero-num'));
  ok('主卡片带最近标签', $('.hero').textContent.includes('最近'));
  ok('列表有内容', $$('.item').length >= 5, $$('.item').length + ' 条');
  const rem = JSON.parse(bridge.reminders);
  ok('提醒已生成并推送', rem.length > 0, rem.length + ' 条');
  ok('提醒按时间升序', rem.every((r, i) => i === 0 || rem[i - 1].at <= r.at));
  ok('提醒字段完整且在未来', rem.every(r => r.title && r.text && r.at > Date.now()), JSON.stringify(rem[0]));
  ok('提醒含农历信息', rem.some(r => r.text.includes('农历')), rem[0].text);

  console.log('=== 3. 全部 / 日历 / 设置 ===');
  clickAct('tab', 'all');
  ok('全部页标题', pageText().includes('全部日子'));
  const beforeSearch = $$('#allList .item').length;
  ok('全部页列出了示例条目', beforeSearch >= 5, beforeSearch + ' 条');
  ok('显示条数一致', $('#allList').textContent.includes('显示 ' + beforeSearch + ' 条'));

  const q = $('#q');
  q.value = '妈妈';
  q.dispatchEvent(new win.Event('input', { bubbles: true }));
  ok('搜索后条数减少', $$('#allList .item').length < beforeSearch, beforeSearch + ' -> ' + $$('#allList .item').length);
  ok('搜索结果正确', $$('#allList .item')[0].textContent.includes('妈妈'));
  ok('搜索不重绘搜索框本身', $('#q') === q);
  q.value = '';
  q.dispatchEvent(new win.Event('input', { bubbles: true }));
  eq('清空搜索后恢复', $$('#allList .item').length, beforeSearch);

  clickAct('filter', 'birthday');
  const bdayCount = $$('#allList .item').length;
  ok('按生日筛选生效', bdayCount > 0 && bdayCount < beforeSearch, bdayCount + ' 条');
  clickAct('filter', 'all');
  clickAct('sort', 'name');
  eq('按名字排序后条数不变', $$('#allList .item').length, beforeSearch);
  clickAct('sort', 'created');
  eq('按添加时间排序后条数不变', $$('#allList .item').length, beforeSearch);
  clickAct('sort', 'date');

  clickAct('tab', 'cal');
  eq('日历格子 42 个', $$('.cal-d').length, 42);
  eq('星期表头 7 个', $$('.cal-w').length, 7);
  ok('有本月节气卡片', pageText().includes('本月节气'));
  eq('本月列出两个节气', $$('.card .row').length, 2);

  const L = win.Lunar;
  const ym = $('.calbar b').textContent.match(/(\d+)年(\d+)月/);
  ok('日历显示年月', !!ym, $('.calbar b').textContent);
  const cy = +ym[1], cm = +ym[2];
  let checked = 0, mism = 0;
  $$('.cal-d').forEach(cell => {
    if (cell.classList.contains('out')) return;
    const d = +cell.querySelector('.g').textContent;
    const lab = cell.querySelector('.l').textContent;
    const expect = L.solarTerm(cy, cm, d) || L.lunarShort(L.solarToLunar(cy, cm, d));
    checked++;
    if (lab !== expect) { mism++; if (mism <= 3) console.log(`      ${cy}-${cm}-${d}: 显示「${lab}」应为「${expect}」`); }
  });
  ok(`日历小字与农历算法完全一致（${checked} 格）`, mism === 0, mism + ' 处不符');

  clickAct('caltoday');
  ok('回到今天', $$('.cal-d.today').length === 1);
  clickAct('calnext');
  ok('翻到下个月', $('.calbar b').textContent.includes('月'));
  clickAct('calprev');
  clickAct('calprev');
  ok('往上翻月不崩', $$('.cal-d').length === 42);
  clickAct('caltoday');
  const cells = $$('.cal-d').filter(c => !c.classList.contains('out'));
  click(cells[0]);
  eq('点选日期后仅一格被选中', $$('.cal-d.sel').length, 1);

  clickAct('tab', 'me');
  ok('设置页有外观', pageText().includes('外观'));
  ok('设置页有提醒', pageText().includes('提醒'));
  ok('设置页有数据', pageText().includes('数据'));
  ok('设置页显示版本', pageText().includes('版本 1.0'));
  clickAct('theme', 'dark');
  eq('切深色', doc.documentElement.getAttribute('data-theme'), 'dark');
  ok('深色底色已同步', bridge.calls.includes('chrome:#141216:false'));
  clickAct('theme', 'light');
  eq('切浅色', doc.documentElement.getAttribute('data-theme'), 'light');
  ok('浅色底色已同步', bridge.calls.includes('chrome:#FBF7F2:true'));
  clickAct('notifyTest');
  ok('测试通知已触发', bridge.calls.includes('testNotification'));
  clickAct('notifyReq');
  ok('申请通知权限已触发', bridge.calls.includes('requestNotify'));
  clickAct('exactAlarm');
  ok('准点提醒设置已触发', bridge.calls.includes('exactAlarm'));

  const d0 = store().settings.defaultRemind.slice();
  clickAct('dremind', '30');
  ok('默认提前天数可添加', store().settings.defaultRemind.indexOf(30) >= 0);
  clickAct('dremind', '30');
  eq('再点一次取消', store().settings.defaultRemind.indexOf(30), -1);
  eq('其余默认值未被破坏', store().settings.defaultRemind.sort().join(','), d0.sort().join(','));

  console.log('=== 4. 新建一条日子 ===');
  clickAct('tab', 'home');
  clickAct('add');
  ok('编辑器已打开', !!$('#f-name'));
  eq('类型四选项', $$('[data-act="fkind"]').length, 4);
  eq('历法两选项', $$('[data-act="fdt"]').length, 2);
  eq('关系六选项', $$('[data-act="frel"]').length, 6);
  eq('提前提醒七选项', $$('[data-act="fremind"]').length, 7);
  $('#f-name').value = '测试生日';
  $('#f-name').dispatchEvent(new win.Event('input', { bubbles: true }));
  clickAct('fdt', 'lunar');
  ok('切农历后月份显示正月', $('#f-month').textContent.includes('正月'), $('#f-month').textContent.slice(0, 30));
  ok('农历日显示初一/三十', $('#f-day').textContent.includes('初一') && $('#f-day').textContent.includes('三十'));
  clickAct('fdt', 'solar');
  ok('切回公历后月份显示 1 月', $('#f-month').textContent.includes('1 月'));
  const solarMax = win.HRCore.solarMonthDays(+$('#f-month').value);
  ok('公历日按所选月份给天数', $('#f-day').textContent.includes(solarMax + ' 日') &&
    !$('#f-day').textContent.includes((solarMax + 1) + ' 日'), '月份天数=' + solarMax);
  clickAct('fdt', 'lunar');
  clickAct('fkind', 'birthday');
  clickAct('frel', '朋友');
  $('#f-year').value = '1990';
  $('#f-year').dispatchEvent(new win.Event('input', { bubbles: true }));
  clickAct('save');
  await tick();
  const created = store().items.find(i => i.name === '测试生日');
  ok('新条目已入库', !!created);
  eq('关系已保存', created.relation, '朋友');
  eq('年份已保存', created.year, 1990);
  eq('历法已保存', created.dateType, 'lunar');
  eq('类型已保存', created.kind, 'birthday');
  ok('条目出现在页面上', !!findRow('测试生日'));
  ok('空名字会被拦截', (function () {
    clickAct('add');
    const n = $('#f-name'); n.value = '   ';
    clickAct('save');
    const blocked = !!$('#f-name');
    clickAct('close');
    return blocked;
  })());

  console.log('=== 5. 详情 / 置顶 / 删除 ===');
  const heroBefore = nameOf($('.hero'));
  click(findRow('测试生日'));
  ok('详情页已打开', sheetText().includes('日子详情'));
  ok('详情显示下次日期', sheetText().includes('下次'));
  ok('详情显示关系', sheetText().includes('朋友'));
  ok('详情显示提前提醒', sheetText().includes('提前提醒'));
  clickAct('pin');
  await tick();
  eq('置顶已写入', store().items.find(i => i.name === '测试生日').pinned, true);
  ok('置顶后出现星标', $$('.pin').length > 0, $$('.pin').length + ' 个');
  eq('置顶不篡改主卡片的「最近」', nameOf($('.hero')), heroBefore);
  eq('置顶的条目排在列表最前', nameOf($$('.item')[0]), '测试生日');

  clickAct('shareOne');
  ok('单条分享已触发', bridge.calls.includes('share'));
  ok('分享内容含名字', bridge.lastShare.includes('测试生日'));

  const before = $$('.item').length;
  clickAct('del');
  await tick();
  ok('删除前有二次确认', sheetText().includes('再想想'));
  clickAct('askyes', '0');
  await tick();
  eq('取消删除后条数不变', $$('.item').length, before);

  click(findRow('测试生日'));
  clickAct('del');
  await tick();
  clickAct('askyes', '1');
  await tick();
  eq('确认删除后条数减一', $$('.item').length, before - 1);
  ok('条目已从页面消失', !pageText().includes('测试生日'));
  ok('条目已从数据中移除', !store().items.some(i => i.name === '测试生日'));

  console.log('=== 6. 节日预设 ===');
  clickAct('tab', 'me');
  const cntBefore = store().items.length;
  clickAct('presets');
  ok('预设面板已打开', sheetText().includes('农历节日'));
  eq('预设共 16 个', $$('[data-act="ptoggle"]').length, 16);
  clickAct('pnone');
  eq('全不选后无选中', $$('[data-act="ptoggle"].on').length, 0);
  clickAct('ptoggle', 'l1_1');
  eq('单选一个', $$('[data-act="ptoggle"].on').length, 1);
  clickAct('pall');
  eq('全选', $$('[data-act="ptoggle"].on').length, 16);
  clickAct('presetadd');
  await tick();
  const cntAfter = store().items.length;
  ok('预设已加入', cntAfter > cntBefore, cntBefore + ' -> ' + cntAfter);
  ok('春节已入库', store().items.some(i => i.name === '春节' && i.dateType === 'lunar'));
  ok('除夕已入库', store().items.some(i => i.name === '除夕'));
  clickAct('presets');
  clickAct('pall');
  clickAct('presetadd');
  await tick();
  eq('重复添加被去重', store().items.length, cntAfter);
  ok('去重有提示', $('#toast').textContent.includes('没有新增'), $('#toast').textContent);

  console.log('=== 7. 备份导出 / 导入 / 分享 ===');
  clickAct('tab', 'me');
  clickAct('fexport');
  ok('导出已调用原生', bridge.calls.some(c => c.startsWith('export:好日子备份-')));
  const payload = JSON.parse(bridge.lastExport);
  eq('导出 app 字段', payload.app, '好日子');
  eq('导出条数一致', payload.items.length, cntAfter);
  ok('导出含 settings', !!payload.settings && !!payload.settings.remindTime);

  clickAct('fshare');
  ok('整体分享已触发', bridge.calls.includes('share'));
  ok('分享内容含标题', bridge.lastShare.includes('好日子'));
  ok('分享内容含分组', bridge.lastShare.includes('▍'));

  const importText = JSON.stringify({
    app: '好日子', v: 1,
    settings: { theme: 'dark', remindTime: '07:30', defaultRemind: [1, 0], notifyOn: true },
    items: [win.HRCore.fixItem({ kind: 'birthday', name: '导入的人', dateType: 'solar', month: 3, day: 3, repeat: 'year' })]
  });

  win.HR.onBackupLoaded(importText);
  await tick();
  ok('导入前有确认', sheetText().includes('覆盖'));
  clickAct('askyes', '1');
  await tick();
  eq('覆盖后只剩 1 条', store().items.length, 1);
  ok('导入的名字出现', pageText().includes('导入的人'));
  eq('导入的设置也生效', store().settings.remindTime, '07:30');
  eq('导入的主题也生效', store().settings.theme, 'dark');
  eq('主题已应用到页面', doc.documentElement.getAttribute('data-theme'), 'dark');

  win.HR.onBackupLoaded(importText);
  await tick();
  clickAct('askyes', '2');
  await tick();
  eq('合并后同 id 去重仍为 1 条', store().items.length, 1);

  win.HR.onBackupLoaded('这不是 JSON');
  await tick();
  ok('坏文件被拒绝', $('#toast').textContent.includes('不是有效的备份文件'), $('#toast').textContent);
  win.HR.onBackupLoaded('{"items":[]}');
  await tick();
  ok('空备份被拒绝', $('#toast').textContent.includes('没有可导入的内容'), $('#toast').textContent);

  console.log('=== 8. 清空 / 返回键 ===');
  clickAct('tab', 'me');
  clickAct('fclear');
  await tick();
  ok('清空前有确认', sheetText().includes('清空'));
  clickAct('askyes', '0');
  await tick();
  eq('取消清空后仍有数据', store().items.length, 1);
  clickAct('fclear');
  await tick();
  clickAct('askyes', '1');
  await tick();
  eq('确认清空后为 0 条', store().items.length, 0);
  ok('回到空状态', pageText().includes('先把重要的日子记下来'));
  eq('清空后提醒同步为空', bridge.reminders, '[]');

  clickAct('tab', 'cal');
  ok('非首页时返回键由页面处理', win.HR.onBack() === true);
  ok('返回后回到近期', $('#page').textContent.includes('先把重要的日子记下来'));
  ok('首页时返回键交给系统', win.HR.onBack() === false);
  clickAct('add');
  ok('有弹层时返回键关闭弹层', win.HR.onBack() === true);
  ok('弹层确已关闭', !$('#sheetWrap').classList.contains('on'));

  console.log('=== 9. 生命周期回调与安全 ===');
  win.HR.onResume();
  win.HR.refresh();
  win.HR.onNativeChange();
  clickAct('tab', 'me');
  ok('生命周期回调不抛异常', true);

  clickAct('tab', 'home');
  clickAct('add');
  const evil = '<img src=x onerror=alert(1)><script>bad<\/script>';
  $('#f-name').value = evil;
  $('#f-name').dispatchEvent(new win.Event('input', { bubbles: true }));
  clickAct('save');
  await tick();
  eq('注入的名字没有变成真实节点', $$('#page img').length, 0);
  eq('注入的 script 没有执行', $$('#page script').length, 0);
  ok('名字按纯文本原样显示', $('#page').textContent.includes('<img src=x'));

  console.log('=== 10. 脚本运行期无未捕获错误 ===');
  eq('jsdom 未捕获脚本错误', scriptErrors.length, 0);
  if (scriptErrors.length) console.log('    ', scriptErrors.slice(0, 3));

  console.log(`\n结果: ${pass} 通过 / ${fail} 失败`);
  process.exit(fail ? 1 : 0);
})().catch(e => {
  console.error('\n测试中断：', e && e.stack || e);
  process.exit(2);
});
